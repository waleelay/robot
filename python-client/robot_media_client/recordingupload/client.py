"""媒体服务文件上传 HTTP 客户端。

本模块只封装上传协议，不关心本地目录扫描和 manifest 状态机。接口顺序通常是：

1. `create_or_resume()`：注册或恢复上传，拿到 fileId/uploadId/part 信息。
2. `part_urls()`：按需批量申请一组 part 的对象存储预签名 PUT URL。
3. `put_part()`：把分片内容直接 PUT 到对象存储，不经过 Media Service。
4. `complete()`：通知 Media Service 所有 part 已上传完毕，可以合并/处理。
5. `status()`：视频进入 PROCESSING 后轮询，直到 READY 或 FAILED。

所有 Media Service JSON 请求都会带 `X-Robot-Id`，用于服务端识别机器人来源。
预签名 URL 的 PUT 请求不依赖该 header，鉴权信息已经包含在 URL 里。
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import BinaryIO

import requests


@dataclass
class UploadResponse:
    """后端创建/恢复上传任务后的响应。

    字段含义：

    - `file_id`：服务端媒体文件 ID，后续状态查询和播放下载都围绕它进行。
    - `upload_id`：multipart 上传会话 ID；为空表示无需继续上传原文件。
    - `status`：服务端当前文件状态，例如 UPLOADING、PROCESSING、READY。
    - `part_size`：每个分片大小，最后一个分片可能小于该值。
    - `part_count`：总分片数。
    - `uploaded_parts`：服务端已确认存在的分片号，用于断点续传跳过。
    """

    file_id: str
    upload_id: str
    status: str
    part_size: int
    part_count: int
    uploaded_parts: set[int]

    @classmethod
    def from_json(cls, data: dict[str, object]) -> "UploadResponse":
        """从后端 JSON 响应解析上传任务状态。

        `uploadedParts` 由后端返回对象数组，这里只保留 partNumber 集合，方便
        `uploader.py` 做 O(1) 判断。
        """
        return cls(
            file_id=str(data.get("fileId") or ""),
            upload_id=str(data.get("uploadId") or ""),
            status=str(data.get("status") or ""),
            part_size=int(data.get("partSize") or 0),
            part_count=int(data.get("partCount") or 0),
            uploaded_parts={
                int(part.get("partNumber") or 0)
                for part in data.get("uploadedParts") or []
                if isinstance(part, dict)
            },
        )


@dataclass
class StatusResponse:
    """文件处理状态响应。

    视频文件 complete 后通常不是马上可播放，而是进入 PROCESSING；状态接口用于
    查询后端 HLS 切片、封面、预览图等处理是否完成。
    """

    status: str
    error_code: str

    @classmethod
    def from_json(cls, data: dict[str, object]) -> "StatusResponse":
        """从后端 JSON 响应解析录像状态。"""
        return cls(status=str(data.get("status") or ""), error_code=str(data.get("errorCode") or ""))


class Client:
    """文件上传 HTTP 客户端，封装机器人侧上传 API。

    该类是薄封装：方法名基本对应后端上传接口，调用方负责维护状态机和重试策略。
    """

    def __init__(self, base_url: str, robot_id: str) -> None:
        """保存后端地址，并在请求头中带上机器人身份。"""
        self.base_url = base_url.rstrip("/")
        self.robot_id = robot_id
        self.session = requests.Session()
        self.session.headers.update({"X-Robot-Id": robot_id})

    def create_or_resume(
        self,
        source_file_id: str,
        device_id: str,
        file_type: str,
        file_name: str,
        content_type: str,
        file_size: int,
    ) -> UploadResponse:
        """向后端创建或恢复一个本地文件的上传任务。

        这个接口是断点续传入口。服务端根据 `sourceFileId` 做幂等判断：

        - 第一次见到该文件：创建 file/upload 记录，并返回待上传 part 信息。
        - 之前上传中断：返回同一个 uploadId 和已上传 part 列表。
        - 原文件已上传完成：返回 fileId/status，但 uploadId 为空。

        调用端不需要自己判断是新上传还是恢复上传，只要按响应继续处理即可。
        """
        response = self._json("POST", "/api/media/files/multipart-uploads", {
            "sourceFileId": source_file_id,
            "deviceId": device_id,
            "fileType": file_type,
            "fileName": file_name,
            "contentType": content_type,
            "fileSize": file_size,
        })
        return UploadResponse.from_json(response)

    def part_urls(self, upload_id: str, part_numbers: list[int]) -> dict[int, str]:
        """批量申请分片上传 URL。

        这里一次请求多个 part URL 是为了减少机器人端与 Media Service 的往返。
        注意它只代表“拿到这些 part 的 PUT 地址”，不代表一定要同时 PUT 这么多
        分片；实际并发由 `RECORDING_UPLOAD_PART_CONCURRENCY` 控制。
        """
        response = self._json(
            "POST",
            f"/api/media/files/multipart-uploads/{upload_id}/part-urls",
            {"partNumbers": part_numbers},
        )
        urls = {
            int(part.get("partNumber") or 0): str(part.get("uploadUrl") or "")
            for part in response.get("parts") or []
            if isinstance(part, dict)
        }
        for part_number in part_numbers:
            if not urls.get(part_number):
                raise RuntimeError(f"part URL missing for part {part_number}")
        return urls

    def put_part(self, url: str, reader: BinaryIO, size: int) -> None:
        """上传单个分片到对象存储预签名 URL。

        `reader` 只暴露当前 part 的字节范围，`size` 是当前 part 的 Content-Length。
        对象存储只有在整个 PUT 请求成功返回 2xx 后，才会认为这个 part 上传完成；
        如果网络中途断开，下次恢复时该 part 不会出现在 uploadedParts 中，会重新上传。
        """
        # PUT 分片直接访问对象存储签名 URL，不再经过 Media Service JSON 接口。
        response = self.session.put(url, data=reader, headers={"Content-Length": str(size)}, timeout=120)
        if response.status_code < 200 or response.status_code >= 300:
            raise RuntimeError(f"part upload returned {response.status_code}: {response.text.strip()}")

    def complete(self, upload_id: str) -> StatusResponse:
        """通知后端所有分片已上传完成。

        complete 之后，服务端会校验 part 完整性并合并对象。非视频文件可能直接 READY；
        视频文件通常会进入 PROCESSING，等待 HLS 切片和相关派生资源生成。
        """
        response = self._json("POST", f"/api/media/files/multipart-uploads/{upload_id}/complete", None)
        return StatusResponse.from_json(response)

    def status(self, file_id: str) -> StatusResponse:
        """查询文件是否已经完成后处理并可使用。

        客户端只轮询状态，不参与 HLS 切片。当前文件 READY 后，播放地址、下载地址
        由前端或业务服务通过 fileId 查询获取。
        """
        response = self._json("GET", f"/api/media/files/{file_id}/status", None)
        return StatusResponse.from_json(response)

    def _json(self, method: str, path: str, body: dict[str, object] | None) -> dict[str, object]:
        """发送 JSON 请求并返回对象响应，非 2xx 时抛出异常。

        这里统一设置 30 秒超时，避免媒体服务异常时上传线程永久阻塞。异常会被
        `Runner._process_with_error_capture()` 捕获并写入 manifest。
        """
        response = self.session.request(method, self.base_url + path, json=body, timeout=30)
        if response.status_code < 200 or response.status_code >= 300:
            raise RuntimeError(f"{response.status_code}: {response.text.strip()}")
        if not response.content:
            return {}
        data = response.json()
        if not isinstance(data, dict):
            return {}
        return data
