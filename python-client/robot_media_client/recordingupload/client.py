from __future__ import annotations

from dataclasses import dataclass
from typing import BinaryIO

import requests


@dataclass
class UploadResponse:
    """后端创建/恢复上传任务后的响应。"""

    file_id: str
    upload_id: str
    status: str
    part_size: int
    part_count: int
    uploaded_parts: set[int]

    @classmethod
    def from_json(cls, data: dict[str, object]) -> "UploadResponse":
        """从后端 JSON 响应解析上传任务状态。"""
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
    """录像处理状态响应。"""

    status: str
    error_code: str

    @classmethod
    def from_json(cls, data: dict[str, object]) -> "StatusResponse":
        """从后端 JSON 响应解析录像状态。"""
        return cls(status=str(data.get("status") or ""), error_code=str(data.get("errorCode") or ""))


class Client:
    """文件上传 HTTP 客户端，封装机器人侧上传 API。"""

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
        """向后端创建或恢复一个本地文件的上传任务。"""
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
        """批量申请分片上传 URL。"""
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
        """上传单个分片到对象存储预签名 URL。"""
        response = self.session.put(url, data=reader, headers={"Content-Length": str(size)}, timeout=120)
        if response.status_code < 200 or response.status_code >= 300:
            raise RuntimeError(f"part upload returned {response.status_code}: {response.text.strip()}")

    def complete(self, upload_id: str) -> StatusResponse:
        """通知后端所有分片已上传完成。"""
        response = self._json("POST", f"/api/media/files/multipart-uploads/{upload_id}/complete", None)
        return StatusResponse.from_json(response)

    def status(self, file_id: str) -> StatusResponse:
        """查询文件是否已经完成后处理并可使用。"""
        response = self._json("GET", f"/api/media/files/{file_id}/status", None)
        return StatusResponse.from_json(response)

    def _json(self, method: str, path: str, body: dict[str, object] | None) -> dict[str, object]:
        """发送 JSON 请求并返回对象响应，非 2xx 时抛出异常。"""
        response = self.session.request(method, self.base_url + path, json=body, timeout=30)
        if response.status_code < 200 or response.status_code >= 300:
            raise RuntimeError(f"{response.status_code}: {response.text.strip()}")
        if not response.content:
            return {}
        data = response.json()
        if not isinstance(data, dict):
            return {}
        return data
