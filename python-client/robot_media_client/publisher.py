from __future__ import annotations

import os
import shlex
import signal
import subprocess
import threading
import time

from .config import Config
from .model import StartCommand


class ProcessPublisher:
    """用外部进程把 RTSP 视频发布到 LiveKit。"""

    def __init__(self, cfg: Config) -> None:
        """保存配置，并按 sessionId 管理 publisher 子进程。"""
        self.cfg = cfg
        self.processes: dict[str, subprocess.Popen[bytes]] = {}
        self.gstreamer_failed_rtsp_urls: set[str] = set()
        self.lock = threading.RLock()

    def start(self, command: StartCommand, rtsp_url: str) -> tuple[str, str]:
        """启动指定 session 的推流；同 session 重复 start 会先清理旧进程。"""
        with self.lock:
            self._stop_locked(command.session_id)
            track_name = "video." + command.channel + "." + command.quality
            if self.cfg.publisher_cmd:
                return self._start_command(command, rtsp_url, track_name, self.cfg.publisher_cmd, "custom")
            if self.cfg.publisher_mode == "ffmpeg":
                if not self.cfg.ffmpeg_publisher_cmd:
                    raise RuntimeError("PUBLISHER_MODE=ffmpeg requires FFMPEG_PUBLISHER_CMD")
                return self._start_command(command, rtsp_url, track_name, self.cfg.ffmpeg_publisher_cmd, "ffmpeg")
            if self.cfg.publisher_mode not in ("auto", "gstreamer"):
                raise RuntimeError(f"unsupported PUBLISHER_MODE: {self.cfg.publisher_mode}")
            if self._should_start_with_ffmpeg(command, rtsp_url):
                return self._start_command(command, rtsp_url, track_name, self.cfg.ffmpeg_publisher_cmd, "ffmpeg-first")
            try:
                result = self._start_gstreamer(command, rtsp_url, track_name)
                if self.cfg.publisher_mode == "auto" and self.cfg.ffmpeg_publisher_cmd:
                    self._watch_gstreamer_for_fallback(command, rtsp_url, track_name)
                return result
            except Exception:
                if self.cfg.publisher_mode == "gstreamer" or not self.cfg.ffmpeg_publisher_cmd:
                    raise
                self.gstreamer_failed_rtsp_urls.add(rtsp_url)
                print("publisher fallback ffmpeg", command.session_id, flush=True)
                return self._start_command(command, rtsp_url, track_name, self.cfg.ffmpeg_publisher_cmd, "ffmpeg")

    def _start_command(
        self,
        command: StartCommand,
        rtsp_url: str,
        track_name: str,
        template: str,
        mode: str,
    ) -> tuple[str, str]:
        """按模板渲染自定义 publisher 命令并启动进程。"""
        args = self._render_args(template, command, rtsp_url, track_name)
        if not args:
            raise RuntimeError("publisher command is empty")
        process = subprocess.Popen(args, stdout=None, stderr=None, start_new_session=True)
        print("publisher command", mode, " ".join(shlex.quote(arg) for arg in args), flush=True)
        self.processes[command.session_id] = process
        self._ensure_running(command.session_id, process)
        return "TR_" + command.session_id, track_name

    def _should_start_with_ffmpeg(self, command: StartCommand, rtsp_url: str) -> bool:
        """在 auto 模式下，对人工指定或已知 GStreamer 失败过的流直接使用 ffmpeg。"""
        if self.cfg.publisher_mode != "auto" or not self.cfg.ffmpeg_publisher_cmd:
            return False
        if rtsp_url in self.gstreamer_failed_rtsp_urls:
            return True
        return command.device_id in self.cfg.publisher_ffmpeg_first_device_ids

    def _watch_gstreamer_for_fallback(self, command: StartCommand, rtsp_url: str, track_name: str) -> None:
        """在 auto 模式下短暂观察 GStreamer 子进程，崩溃时自动切到 ffmpeg。"""
        process = self.processes.get(command.session_id)
        if process is None:
            return
        thread = threading.Thread(
            target=self._fallback_if_gstreamer_exits,
            args=(command, rtsp_url, track_name, process),
            name=f"publisher-fallback-{command.session_id}",
            daemon=True,
        )
        thread.start()

    def _fallback_if_gstreamer_exits(
        self,
        command: StartCommand,
        rtsp_url: str,
        track_name: str,
        process: subprocess.Popen[bytes],
    ) -> None:
        """如果 GStreamer 直推在观察窗口内退出，且 session 仍有效，就改用 ffmpeg 推同一路流。"""
        deadline = time.monotonic() + self.cfg.publisher_fallback_watch_seconds
        while time.monotonic() < deadline:
            code = process.poll()
            if code is None:
                time.sleep(0.2)
                continue
            with self.lock:
                if self.processes.get(command.session_id) is not process:
                    return
                self.processes.pop(command.session_id, None)
                self.gstreamer_failed_rtsp_urls.add(rtsp_url)
                print(
                    "publisher auto fallback ffmpeg",
                    command.session_id,
                    f"gstreamer_exit={code}",
                    flush=True,
                )
                try:
                    self._start_command(command, rtsp_url, track_name, self.cfg.ffmpeg_publisher_cmd, "ffmpeg")
                except Exception as exc:
                    print("publisher auto fallback failed", command.session_id, exc, flush=True)
            return

    def _start_gstreamer(self, command: StartCommand, rtsp_url: str, track_name: str) -> tuple[str, str]:
        """使用 gstreamer-publisher 默认路径发布 RTSP 到 LiveKit。"""
        pipeline = self._render_text(self.cfg.gstreamer_pipeline, command, rtsp_url, track_name)
        args = [
            self.cfg.gstreamer_publisher_path,
            "--url",
            command.livekit_url,
            "--token",
            command.publisher_token,
            "--",
        ]
        args.extend(shlex.split(pipeline))
        process = subprocess.Popen(args, stdout=None, stderr=None, start_new_session=True)
        print("publisher command", " ".join(shlex.quote(arg) for arg in args), flush=True)
        self.processes[command.session_id] = process
        self._ensure_running(command.session_id, process)
        return "TR_" + command.session_id, track_name

    def stop(self, session_id: str) -> None:
        """停止指定 session 的 publisher 进程。"""
        with self.lock:
            self._stop_locked(session_id)

    def stop_all(self) -> None:
        """停止当前客户端管理的所有 publisher 进程。"""
        with self.lock:
            for session_id in list(self.processes.keys()):
                self._stop_locked(session_id)

    def _stop_locked(self, session_id: str) -> None:
        """在已持锁的情况下停止并移除一个 publisher 进程。"""
        process = self.processes.pop(session_id, None)
        if process is None:
            return
        if process.poll() is not None:
            return
        try:
            os.killpg(process.pid, signal.SIGKILL)
        except OSError:
            process.kill()

    def _ensure_running(self, session_id: str, process: subprocess.Popen[bytes]) -> None:
        """短暂观察子进程是否立即退出，尽早暴露 pipeline 参数错误。"""
        deadline = time.monotonic() + 2
        while time.monotonic() < deadline:
            code = process.poll()
            if code is not None:
                self.processes.pop(session_id, None)
                raise RuntimeError(f"publisher exited with code {code}")
            time.sleep(0.05)

    def _render_args(self, template: str, command: StartCommand, rtsp_url: str, track_name: str) -> list[str]:
        """把命令模板拆分为 argv，并替换会话、RTSP 和 LiveKit 占位符。"""
        return [
            self._render_text(part, command, rtsp_url, track_name)
            for part in shlex.split(template)
        ]

    @staticmethod
    def _render_text(template: str, command: StartCommand, rtsp_url: str, track_name: str) -> str:
        """替换单个模板片段中的占位符。"""
        return (
            template
            .replace("{rtsp}", rtsp_url)
            .replace("{livekitUrl}", command.livekit_url)
            .replace("{token}", command.publisher_token)
            .replace("{room}", command.room_name)
            .replace("{track}", track_name)
        )
