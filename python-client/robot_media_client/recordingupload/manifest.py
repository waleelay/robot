from __future__ import annotations

import json
import os
import threading
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path

from ..timeutil import isoformat, now_utc, parse_time


@dataclass
class Task:
    """本地录像文件的一条上传任务记录。"""

    source_file_id: str
    file_path: str
    file_size: int
    created_at: datetime
    file_id: str = ""
    upload_id: str = ""
    status: str = "PENDING"
    error: str = ""
    updated_at: datetime | None = None

    @classmethod
    def from_json(cls, data: dict[str, object]) -> "Task":
        """从 manifest JSON 恢复上传任务。"""
        return cls(
            source_file_id=str(data.get("sourceFileId") or ""),
            file_path=str(data.get("filePath") or ""),
            file_size=int(data.get("fileSize") or 0),
            created_at=parse_time(data.get("createdAt")) or now_utc(),
            file_id=str(data.get("fileId") or data.get("recordingId") or ""),
            upload_id=str(data.get("uploadId") or ""),
            status=str(data.get("status") or "PENDING"),
            error=str(data.get("error") or ""),
            updated_at=parse_time(data.get("updatedAt")) or now_utc(),
        )

    def to_json(self) -> dict[str, object]:
        """把上传任务序列化到 manifest 文件。"""
        data: dict[str, object] = {
            "sourceFileId": self.source_file_id,
            "filePath": self.file_path,
            "fileSize": self.file_size,
            "createdAt": isoformat(self.created_at),
            "status": self.status,
            "updatedAt": isoformat(self.updated_at or now_utc()),
        }
        if self.file_id:
            data["fileId"] = self.file_id
        if self.upload_id:
            data["uploadId"] = self.upload_id
        if self.error:
            data["error"] = self.error
        return data


class Manifest:
    """本地上传任务清单，负责断点续传状态持久化。"""

    def __init__(self, path: str) -> None:
        """保存 manifest 路径并初始化任务表。"""
        self.path = path
        self.tasks: dict[str, Task] = {}
        self.lock = threading.RLock()

    @classmethod
    def load(cls, path: str) -> "Manifest":
        """加载 manifest 文件；文件不存在或损坏时返回空清单。"""
        manifest = cls(path)
        try:
            with open(path, "r", encoding="utf-8") as handle:
                data = json.load(handle)
        except (FileNotFoundError, json.JSONDecodeError):
            return manifest
        for source_id, task_data in (data.get("tasks") or {}).items():
            if isinstance(task_data, dict):
                task = Task.from_json(task_data)
                manifest.tasks[source_id] = task
        return manifest

    def update(self, task: Task) -> None:
        """更新一个任务并把完整 manifest 写回磁盘。"""
        with self.lock:
            self.tasks[task.source_file_id] = task
            directory = os.path.dirname(self.path)
            if directory and directory != ".":
                Path(directory).mkdir(parents=True, exist_ok=True)
            data = {
                "tasks": {
                    source_id: item.to_json()
                    for source_id, item in self.tasks.items()
                }
            }
            with open(self.path, "w", encoding="utf-8") as handle:
                json.dump(data, handle, ensure_ascii=False, indent=2)
