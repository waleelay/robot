from __future__ import annotations

from concurrent.futures import FIRST_EXCEPTION, ThreadPoolExecutor, wait
import os
from typing import BinaryIO

from .client import Client, UploadResponse


class PreadReader:
    """基于 os.pread 的分片 reader，避免多线程共享文件游标互相干扰。"""

    def __init__(self, fd: int, offset: int, size: int) -> None:
        """保存文件描述符、分片起始偏移和剩余大小。"""
        self.fd = fd
        self.offset = offset
        self.remaining = size

    def read(self, size: int = -1) -> bytes:
        """读取当前分片的一段字节，并推进本 reader 的偏移。"""
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
    """并发上传服务端尚未收到的录像分片。"""
    worker_count = max(1, concurrency)
    batch_size = max(1, url_batch_size)
    part_numbers: list[int] = []
    futures = []
    with ThreadPoolExecutor(max_workers=worker_count) as executor:
        for part_number in range(1, upload.part_count + 1):
            if part_number in upload.uploaded_parts:
                continue
            part_numbers.append(part_number)
            if len(part_numbers) < batch_size:
                continue
            futures.extend(_submit_batch(executor, client, file, file_size, upload, part_numbers))
            part_numbers = []
        if part_numbers:
            futures.extend(_submit_batch(executor, client, file, file_size, upload, part_numbers))
        done, pending = wait(futures, return_when=FIRST_EXCEPTION)
        for item in done:
            item.result()
        for item in pending:
            item.result()


def _submit_batch(
    executor: ThreadPoolExecutor,
    client: Client,
    file: BinaryIO,
    file_size: int,
    upload: UploadResponse,
    part_numbers: list[int],
):
    """申请一批分片 URL，并把上传任务提交到线程池。"""
    urls = client.part_urls(upload.upload_id, part_numbers)
    return [
        executor.submit(_upload_part, client, file, file_size, upload, part_number, urls[part_number])
        for part_number in part_numbers
    ]


def _upload_part(client: Client, file: BinaryIO, file_size: int, upload: UploadResponse, part_number: int, url: str) -> None:
    """上传单个分片。"""
    offset = (part_number - 1) * upload.part_size
    size = min(upload.part_size, file_size - offset)
    reader = PreadReader(file.fileno(), offset, size)
    client.put_part(url, reader, size)
