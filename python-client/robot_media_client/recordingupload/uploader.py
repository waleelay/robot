"""multipart 分片上传执行模块。

本模块负责“怎么把一个本地文件切成 part 并发 PUT 到对象存储”。它不直接修改
manifest，也不调用 complete；调用方需要在本函数成功返回后再调用
`Client.complete()`。

断点续传依赖两个条件：

1. 后端 create_or_resume 返回 `uploaded_parts`，告诉客户端哪些 part 已经存在。
2. 本模块只上传缺失 part。任意 part PUT 失败都会抛出异常，下一轮扫描时重新
   create_or_resume，再从新的 uploaded_parts 继续。
"""

from __future__ import annotations

from concurrent.futures import FIRST_EXCEPTION, ThreadPoolExecutor, wait
import os
from typing import BinaryIO

from .client import Client, UploadResponse


class PreadReader:
    """基于 os.pread 的分片 reader，避免多线程共享文件游标互相干扰。

    requests 在上传时会不断调用 `read()`。如果多个线程共享同一个文件对象并用
    seek/read，文件游标会互相覆盖，导致上传错位。`os.pread(fd, size, offset)`
    每次都显式指定偏移，不改变全局文件游标，因此适合多线程分片上传。
    """

    def __init__(self, fd: int, offset: int, size: int) -> None:
        """保存文件描述符、分片起始偏移和剩余大小。"""
        self.fd = fd
        self.offset = offset
        self.remaining = size

    def read(self, size: int = -1) -> bytes:
        """读取当前分片的一段字节，并推进本 reader 自己的偏移。

        当 remaining 归零时返回空字节，requests 会认为请求体已经读取完毕。
        """
        if self.remaining <= 0:
            return b""
        if size < 0 or size > self.remaining:
            size = self.remaining
        data = os.pread(self.fd, size, self.offset)
        self.offset += len(data)
        self.remaining -= len(data)
        return data


def upload_missing_parts(
    client: Client,
    file: BinaryIO,
    file_size: int,
    upload: UploadResponse,
    concurrency: int,
    url_batch_size: int,
) -> None:
    """并发上传服务端尚未收到的文件分片。

    参数说明：

    - `file_size`：本地文件总大小，用于计算最后一个 part 的真实长度。
    - `upload.part_size`：服务端指定的分片大小，客户端不能自行改变。
    - `upload.uploaded_parts`：服务端已确认的 part，函数会直接跳过。
    - `concurrency`：同时 PUT 的最大分片数。
    - `url_batch_size`：每次向 Media Service 批量申请多少个 part URL。

    `url_batch_size` 可以大于 `concurrency`。这表示一次多拿一些 URL 减少接口
    调用次数，但真正 PUT 仍由线程池限制，不会同时发起超过 `concurrency` 个
    上传请求。
    """
    worker_count = max(1, concurrency)
    batch_size = max(1, url_batch_size)
    part_numbers: list[int] = []
    futures = []
    with ThreadPoolExecutor(max_workers=worker_count) as executor:
        for part_number in range(1, upload.part_count + 1):
            if part_number in upload.uploaded_parts:
                # 已存在的 part 不再上传。对于断点续传，这一步可以跳过之前已成功的分片。
                continue
            part_numbers.append(part_number)
            if len(part_numbers) < batch_size:
                continue
            # URL 批量申请数量和 PUT 并发数量是两个独立配置：先拿一批 URL，再由线程池控制实际并发。
            futures.extend(_submit_batch(executor, client, file, file_size, upload, part_numbers))
            part_numbers = []
        if part_numbers:
            futures.extend(_submit_batch(executor, client, file, file_size, upload, part_numbers))
        done, pending = wait(futures, return_when=FIRST_EXCEPTION)
        for item in done:
            item.result()
        for item in pending:
            # 如果已经有任务失败，这里继续读取 pending 结果，确保线程池里的异常不会被吞掉。
            # 失败会抛给 Runner，由 Runner 写入 manifest，等待下一轮扫描重试。
            item.result()


def _submit_batch(
    executor: ThreadPoolExecutor,
    client: Client,
    file: BinaryIO,
    file_size: int,
    upload: UploadResponse,
    part_numbers: list[int],
):
    """申请一批分片 URL，并把上传任务提交到线程池。

    part URL 有有效期，所以这里采用“申请一批、立刻提交一批”的模式，避免一次性
    为整个大文件申请过多 URL 后长时间排队导致过期。
    """
    urls = client.part_urls(upload.upload_id, part_numbers)
    return [
        executor.submit(_upload_part, client, file, file_size, upload, part_number, urls[part_number])
        for part_number in part_numbers
    ]


def _upload_part(client: Client, file: BinaryIO, file_size: int, upload: UploadResponse, part_number: int, url: str) -> None:
    """上传单个分片。

    partNumber 从 1 开始，offset 按 `(partNumber - 1) * partSize` 计算。
    最后一个分片长度用文件总大小截断，避免读取超过文件末尾。
    """
    offset = (part_number - 1) * upload.part_size
    size = min(upload.part_size, file_size - offset)
    reader = PreadReader(file.fileno(), offset, size)
    client.put_part(url, reader, size)
