"""本地文件上传后台任务。

本模块是 Python 客户端的视频/文件上传入口，设计目标是让机器人端只要把录像、
配置、日志等文件放到 `RECORDING_DIRECTORY`，客户端就能后台发现、断点续传、
完成上传，并在视频进入服务端 HLS 后处理阶段后持续轮询状态。

整体链路：

1. `discover()` 扫描本地目录，把新文件登记为 manifest 任务。
2. `process()` 调 Media Service 创建或恢复 multipart 上传。
3. `upload_missing_parts()` 只上传服务端尚未收到的 part。
4. `complete()` 通知服务端合并对象；视频通常进入 `PROCESSING`。
5. 后续轮询 `/api/media/files/{fileId}/status`，直到文件 `READY` 或失败。
6. `enforce_retention()` 只清理服务端已 READY 的本地文件。
"""

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
    """文件上传后台轮询器，扫描本地文件并上传到媒体服务。

    Runner 与 MQTT 无关，随客户端启动后常驻后台线程。即使 MQTT 断开，上传
    runner 也可以继续工作；如果进程退出，下次启动会通过 manifest 恢复上传。
    """

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
        """执行一次完整上传循环。

        一个扫描周期分三步：

        1. 发现新文件并写入 manifest。
        2. 按本地缓存策略清理已经 READY 的历史文件。
        3. 按 `RECORDING_UPLOAD_FILE_CONCURRENCY` 并发推进未完成任务。

        注意：单文件内部的分片 PUT 并发由 `RECORDING_UPLOAD_PART_CONCURRENCY`
        控制，和这里的多文件并发是两层不同的并发。
        """
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
        """处理单个任务并把异常写入 manifest。

        上传失败不会删除任务，也不会删除本地文件。错误信息写入 manifest 后，
        下一个扫描周期会继续尝试同一个任务，从而实现最小化的自动重试。
        """
        try:
            self.process(task)
        except Exception as exc:
            task.error = str(exc)
            task.updated_at = now_utc()
            self.manifest.update(task)
            print("recording upload", task.file_path, exc, flush=True)

    def discover(self) -> None:
        """扫描目录，把新的非空普通文件登记到 manifest。

        当前只扫描 `RECORDING_DIRECTORY` 的第一层普通文件，不递归子目录。
        隐藏文件和 0 字节文件会被跳过，避免上传临时文件或还未产生内容的文件。

        这里不直接访问 Media Service，只负责建立本地任务索引；真正上传在
        `process()` 中执行。这样即使媒体服务暂时不可用，文件发现结果也能先
        保存在 manifest 里。
        """
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
            # sourceFileId 是客户端和服务端共同使用的幂等键。
            #
            # 组成字段：
            # - recordingDeviceID：表示文件归属设备，默认 camera01。
            # - fileName：保留源文件名，便于后端展示和排查。
            # - fileSize + mtime：用于区分“同名覆盖后的新文件”。
            #
            # 因此同一个 mp4 如果只是重复启动客户端，不会重复上传；如果你为了
            # 测试重新拷贝/覆盖该文件，size 或 mtime 变化后会生成新的 sourceFileId。
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
        """推进单个文件上传任务的状态机。

        这个方法是上传流程主干，按当前 task 状态做不同事情：

        - `PENDING/UPLOADING/FAILED`：调用 create_or_resume，向后端创建或恢复上传。
        - `PROCESSING`：说明原始文件已经上传完成，只轮询服务端处理状态。
        - `READY/LOCAL_DELETED`：在 `run_once()` 入口已跳过，不会进入这里。

        对视频来说，`complete()` 成功只代表原始文件的分片已经全部上传并完成
        服务端合并；能否播放还取决于后端后处理是否完成，例如 HLS 切片、封面、
        预览图生成等。
        """
        print(
            "file upload processing",
            f"file={task.file_path}",
            f"status={task.status}",
            f"fileId={task.file_id}",
            f"uploadId={task.upload_id}",
            flush=True,
        )
        if task.file_id and waiting_for_playback(task.status):
            # 原文件上传完成后，视频还需要等待服务端校验、HLS 切片和缩略图生成。
            #
            # 这里不再 PUT 原始文件，只查询服务端状态：
            # - READY：前端可以播放或下载。
            # - PROCESSING：服务端仍在处理，下个扫描周期继续查。
            # - FAILED：服务端处理失败，错误码写入 manifest，便于本地排查。
            response = self.client.status(task.file_id)
            task.status = response.status
            task.error = response.error_code
            task.updated_at = now_utc()
            self.manifest.update(task)
            return
        path = Path(task.file_path)
        stat = path.stat()
        # create_or_resume 是断点续传的核心入口。
        #
        # 客户端把 sourceFileId、文件名、大小、类型发给后端，后端会返回：
        # - fileId：媒体文件记录 ID。
        # - uploadId：需要继续上传时返回；如果为空，表示无需再传原文件。
        # - partSize/partCount：服务端决定的分片大小和总分片数。
        # - uploadedParts：对象存储中已经存在的 part，用于跳过已传分片。
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
            # 服务端识别到该 sourceFileId 已上传、已完成或正在处理时，会返回空 uploadId。
            # 客户端只同步服务端状态，不再重复 PUT 分片；这也是进程重启后避免重复
            # 上传大文件的关键路径。
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
            # upload_missing_parts 会根据 uploadedParts 跳过已上传分片，并按配置并发 PUT。
            # 如果中途网络断开，未完成的 part 下次会重新申请 URL 后再传；已完成的 part
            # 由后端 create_or_resume 返回的 uploadedParts 识别。
            upload_missing_parts(
                self.client,
                handle,
                stat.st_size,
                upload,
                self.cfg.upload_part_concurrency,
                self.cfg.upload_part_url_batch_size,
            )
        # 所有缺失 part PUT 成功后，必须调用 complete。服务端会在这里校验 part、
        # 完成对象存储合并，并把文件推进到 READY 或 PROCESSING。
        response = self.client.complete(upload.upload_id)
        print(f"file upload original file completed fileId={upload.file_id}", flush=True)
        task.status = response.status
        task.error = response.error_code
        task.updated_at = now_utc()
        self.manifest.update(task)

    def enforce_retention(self) -> None:
        """按容量、剩余空间和保留时长清理已经 READY 的本地录像文件。

        清理策略只作用于 `READY` 文件，避免删除仍在上传或仍在服务端处理的视频。
        删除后只更新 manifest 状态为 `LOCAL_DELETED`，不会删除服务端文件。
        """
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
