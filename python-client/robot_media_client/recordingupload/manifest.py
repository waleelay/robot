"""本地上传 manifest 持久化模块。

manifest 是机器人端断点续传的本地索引，默认文件名为
`recording-upload-manifest.json`。它不保存文件内容，只保存“本地文件 -> 服务端
上传会话”的映射关系。

为什么需要 manifest：

- 客户端重启后能知道哪些文件已经发现过，避免重复创建上传任务。
- 上传到一半断开后能继续使用同一个 sourceFileId 恢复上传。
- 视频 complete 后进入 PROCESSING 时，客户端能记住 fileId 并继续轮询状态。
- 本地文件被清理后仍能保留 LOCAL_DELETED 状态，避免同一源文件被重复处理。
"""

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
    """本地文件的一条上传任务记录。

    重要字段：

    - `source_file_id`：幂等键，也是 manifest.tasks 的 key。
    - `file_path`：本地文件路径，用于继续读取和上传。
    - `file_id`：服务端媒体文件 ID；complete 后轮询状态需要它。
    - `upload_id`：multipart 上传会话 ID；上传原始文件时需要它。
    - `status`：客户端本地状态，常见值为 PENDING、UPLOADING、PROCESSING、
      READY、LOCAL_DELETED。
    """

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
        """从 manifest JSON 恢复上传任务。

        这里尽量宽松解析，避免 manifest 中单个字段缺失导致整个上传清单无法加载。
        """
        return cls(
            source_file_id=str(data.get("sourceFileId") or ""),
            file_path=str(data.get("filePath") or ""),
            file_size=int(data.get("fileSize") or 0),
            created_at=parse_time(data.get("createdAt")) or now_utc(),
            # 兼容旧版本 manifest 中的 recordingId，升级后不会丢失断点续传状态。
            file_id=str(data.get("fileId") or data.get("recordingId") or ""),
            upload_id=str(data.get("uploadId") or ""),
            status=str(data.get("status") or "PENDING"),
            error=str(data.get("error") or ""),
            updated_at=parse_time(data.get("updatedAt")) or now_utc(),
        )

    def to_json(self) -> dict[str, object]:
        """把上传任务序列化到 manifest 文件。

        空字段不写入 JSON，减少清单噪音；必要字段始终保留，保证重启后能恢复状态。
        """
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
    """本地上传任务清单，负责断点续传状态持久化。

    当前实现每次更新都会把完整 tasks 写回磁盘。上传任务数量通常不大，这种方式
    简单可靠；如果未来同一机器人长期积累大量任务，可以再考虑压缩或归档。
    """

    def __init__(self, path: str) -> None:
        """保存 manifest 路径并初始化任务表。"""
        self.path = path
        self.tasks: dict[str, Task] = {}
        self.lock = threading.RLock()

    @classmethod
    def load(cls, path: str) -> "Manifest":
        """加载 manifest 文件；文件不存在或损坏时返回空清单。

        损坏时不抛异常是为了保证客户端能启动；代价是本地已知任务会丢失，后续会
        重新发现目录中的文件，并由服务端 sourceFileId 幂等逻辑兜底。
        """
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
        """更新一个任务并把完整 manifest 写回磁盘。

        这个方法会在任务状态变化时频繁调用，例如发现文件、开始上传、complete 后、
        轮询状态变化、本地清理完成等。写入使用 `ensure_ascii=False`，便于直接查看
        中文文件名。
        """
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
