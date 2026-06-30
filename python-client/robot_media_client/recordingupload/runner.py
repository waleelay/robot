from __future__ import annotations

import mimetypes
import os
import threading
import time
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from pathlib import Path

from ..config import Config
from ..timeutil import now_utc
from .client import Client
from .manifest import Manifest, Task
from .uploader import upload_missing_parts


class Runner:
    """文件上传后台轮询器，扫描本地文件并上传到媒体服务。"""

    def __init__(self, cfg: Config) -> None:
        """初始化 HTTP 客户端、manifest 和停止事件。"""
        self.cfg = cfg
        self.client = Client(cfg.media_service_url, cfg.robot_id)
        self.manifest = Manifest.load(cfg.recording_manifest_path)
        self.stop_event = threading.Event()

    def run(self) -> None:
        """持续执行扫描、上传和本地缓存清理，直到收到停止信号。"""
        print(
            "recording upload runner started",
            f"robotId={self.cfg.robot_id}",
            f"mediaService={self.cfg.media_service_url}",
            f"dir={self.cfg.recording_directory}",
            f"manifest={self.cfg.recording_manifest_path}",
            f"loadedTasks={len(self.manifest.tasks)}",
            flush=True,
        )
        while not self.stop_event.is_set():
            self.run_once()
            self.stop_event.wait(self.cfg.upload_scan_interval)

    def stop(self) -> None:
        """请求上传后台线程退出。"""
        self.stop_event.set()

    def run_once(self) -> None:
        """执行一次完整上传循环。"""
        self.discover()
        self.enforce_retention()
        tasks = sorted(self.manifest.tasks.values(), key=lambda task: task.created_at)
        worker_count = max(1, self.cfg.upload_file_concurrency)
        with ThreadPoolExecutor(max_workers=worker_count) as executor:
            futures = []
            for task in tasks:
                if task.status in {"READY", "LOCAL_DELETED"}:
                    continue
                futures.append(executor.submit(self._process_with_error_capture, task))
            for future in futures:
                future.result()

    def _process_with_error_capture(self, task: Task) -> None:
        """处理单个任务并把异常写入 manifest。"""
        try:
            self.process(task)
        except Exception as exc:
            task.error = str(exc)
            task.updated_at = now_utc()
            self.manifest.update(task)
            print("recording upload", task.file_path, exc, flush=True)

    def discover(self) -> None:
        """扫描目录，把新的非空普通文件登记到 manifest。"""
        root = Path(self.cfg.recording_directory)
        files = sorted(path for path in root.glob("*") if path.is_file())
        if not files:
            print(f"recording upload scan found no files dir={self.cfg.recording_directory}", flush=True)
        for path in files:
            try:
                stat = path.stat()
            except FileNotFoundError:
                continue
            if stat.st_size == 0 or path.name.startswith("."):
                continue
            source_id = f"{self.cfg.recording_device_id}/{path.name}/{stat.st_size}/{int(stat.st_mtime)}"
            if source_id in self.manifest.tasks:
                continue
            print(f"recording upload discovered file={path} size={stat.st_size}", flush=True)
            self.manifest.update(Task(
                source_file_id=source_id,
                file_path=str(path),
                file_size=stat.st_size,
                created_at=datetime.fromtimestamp(stat.st_mtime, timezone.utc),
                status="PENDING",
                updated_at=now_utc(),
            ))

    def process(self, task: Task) -> None:
        """推进单个文件上传任务的状态机。"""
        print(
            "file upload processing",
            f"file={task.file_path}",
            f"status={task.status}",
            f"fileId={task.file_id}",
            f"uploadId={task.upload_id}",
            flush=True,
        )
        if task.file_id and waiting_for_playback(task.status):
            response = self.client.status(task.file_id)
            task.status = response.status
            task.error = response.error_code
            task.updated_at = now_utc()
            self.manifest.update(task)
            return
        path = Path(task.file_path)
        stat = path.stat()
        upload = self.client.create_or_resume(
            source_file_id=task.source_file_id,
            device_id=self.cfg.recording_device_id,
            file_type=file_type(path.name),
            file_name=path.name,
            content_type=content_type(path.name),
            file_size=stat.st_size,
        )
        task.file_id = upload.file_id
        task.upload_id = upload.upload_id
        if not upload.upload_id:
            print(
                "file upload server says upload not required",
                f"fileId={upload.file_id}",
                f"status={upload.status}",
                flush=True,
            )
            task.status = upload.status
            task.updated_at = now_utc()
            self.manifest.update(task)
            return
        task.status = "UPLOADING"
        self.manifest.update(task)
        print(
            "file upload started",
            f"fileId={upload.file_id}",
            f"uploadId={upload.upload_id}",
            f"parts={upload.part_count}",
            f"partSize={upload.part_size}",
            flush=True,
        )
        with open(path, "rb") as handle:
            upload_missing_parts(
                self.client,
                handle,
                stat.st_size,
                upload,
                self.cfg.upload_part_concurrency,
                self.cfg.upload_part_url_batch_size,
            )
        response = self.client.complete(upload.upload_id)
        print(f"file upload original file completed fileId={upload.file_id}", flush=True)
        task.status = response.status
        task.error = response.error_code
        task.updated_at = now_utc()
        self.manifest.update(task)

    def enforce_retention(self) -> None:
        """按容量、剩余空间和保留时长清理已经 READY 的本地录像文件。"""
        tasks: list[Task] = []
        cache_bytes = 0
        for task in self.manifest.tasks.values():
            try:
                stat = os.stat(task.file_path)
            except FileNotFoundError:
                continue
            task.file_size = stat.st_size
            cache_bytes += stat.st_size
            tasks.append(task)
        tasks.sort(key=lambda task: task.created_at)
        need_space = cache_bytes > self.cfg.local_cache_max_bytes or free_bytes(self.cfg.recording_directory) < self.cfg.local_min_free_bytes
        for task in tasks:
            if task.status != "READY":
                continue
            updated_at = task.updated_at or now_utc()
            if not need_space and (now_utc() - updated_at).total_seconds() < self.cfg.local_retention_after_ready:
                continue
            try:
                os.remove(task.file_path)
            except FileNotFoundError:
                pass
            except OSError as exc:
                print("recording local retention remove", task.file_path, exc, flush=True)
                continue
            task.status = "LOCAL_DELETED"
            task.updated_at = now_utc()
            self.manifest.update(task)
            cache_bytes -= task.file_size
            need_space = cache_bytes > self.cfg.local_cache_max_bytes or free_bytes(self.cfg.recording_directory) < self.cfg.local_min_free_bytes
            if not need_space:
                return


def waiting_for_playback(status: str) -> bool:
    """判断任务是否处于等待后端转码/回放就绪的状态。"""
    return status == "PROCESSING"


def file_type(name: str) -> str:
    """根据文件后缀映射后端通用文件类型。"""
    suffix = Path(name).suffix.lower()
    if suffix in {".mp4", ".mov", ".m4v"}:
        return "VIDEO"
    if suffix in {".jpg", ".jpeg", ".png", ".webp"}:
        return "IMAGE"
    if suffix in {".log", ".txt"}:
        return "LOG"
    if suffix in {".json", ".yaml", ".yml", ".toml", ".ini", ".conf"}:
        return "CONFIG"
    if suffix == ".map":
        return "MAP"
    if suffix in {".pdf", ".doc", ".docx", ".xls", ".xlsx"}:
        return "DOCUMENT"
    return "OTHER"


def content_type(name: str) -> str:
    """根据文件名推断 Content-Type，未知时按二进制处理。"""
    guessed, _ = mimetypes.guess_type(name)
    return guessed or "application/octet-stream"


def free_bytes(path: str) -> int:
    """返回指定路径所在文件系统的可用字节数。"""
    target = path
    if not os.path.exists(target):
        target = os.path.dirname(path) or "."
    stat = os.statvfs(target)
    return stat.f_bavail * stat.f_frsize
