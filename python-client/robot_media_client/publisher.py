"""外部视频推流进程管理模块。

本模块只负责把“已经确认可访问的 RTSP 地址”交给外部进程发布到 LiveKit。
RTSP 地址解析和探测发生在 `mqtt_client.py`，这里不再重复探测。

推流模式优先级：

1. `PUBLISHER_CMD` 非空：完全使用自定义命令，适合现场临时接入特殊推流器。
2. `PUBLISHER_MODE=ffmpeg`：直接使用 `FFMPEG_PUBLISHER_CMD`。
3. `PUBLISHER_MODE=gstreamer`：只使用 `gstreamer-publisher`，失败直接抛错。
4. `PUBLISHER_MODE=auto`：优先 GStreamer，失败或短时间退出时回退 FFmpeg。

所有外部进程都按 sessionId 管理。收到同一个 sessionId 的新 start 时，会先停止
旧进程再启动新进程；收到 stop 或 MQTT 断线时，会杀掉对应进程组，避免后台残留
继续占用摄像头或 LiveKit 房间。
"""

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
    """用外部进程把 RTSP 视频发布到 LiveKit。

    类本身不直接处理媒体流，只负责：

    - 选择 GStreamer、FFmpeg 或自定义命令。
    - 渲染命令模板中的 RTSP/LiveKit/token 占位符。
    - 启停和观察外部 publisher 子进程。
    - 为上层返回稳定的 trackSid/trackName 占位值。
    """

    def __init__(self, cfg: Config) -> None:
        """保存配置，并按 sessionId 管理 publisher 子进程。

        `gstreamer_failed_rtsp_urls` 是 auto 模式下的本地经验缓存：某个 RTSP URL
        已经触发过 GStreamer 失败后，后续同 URL 直接先走 FFmpeg，减少失败等待。
        """
        self.cfg = cfg
        self.processes: dict[str, subprocess.Popen[bytes]] = {}
        self.gstreamer_failed_rtsp_urls: set[str] = set()
        self.lock = threading.RLock()

    def start(self, command: StartCommand, rtsp_url: str) -> tuple[str, str]:
        """启动指定 session 的推流；同 session 重复 start 会先清理旧进程。

        `command` 来自 MQTT start/switch-channel 指令，里面包含 LiveKit URL、
        publisher token、roomName、channel、quality 等信息。`rtsp_url` 是上层
        已经解析并通过 ffprobe 探测的本地摄像头地址。

        返回值：

        - trackSid：当前外部 publisher 不会把真实 LiveKit track sid 回传给父进程，
          因此客户端用 `TR_{sessionId}` 作为稳定占位值。
        - trackName：按 `video.{channel}.{quality}` 生成，便于后端/前端识别。
        """
        with self.lock:
            # switch-channel 或重复 start 可能复用同一个 sessionId；先停旧进程，保证
            # 同一 session 只有一个 publisher 占用资源。
            self._stop_locked(command.session_id)
            track_name = "video." + command.channel + "." + command.quality
            if self.cfg.publisher_cmd:
                # PUBLISHER_CMD 是最高优先级逃生口，适合现场验证新 pipeline。
                return self._start_command(command, rtsp_url, track_name, self.cfg.publisher_cmd, "custom")
            if self.cfg.publisher_mode == "ffmpeg":
                if not self.cfg.ffmpeg_publisher_cmd:
                    raise RuntimeError("PUBLISHER_MODE=ffmpeg requires FFMPEG_PUBLISHER_CMD")
                # 强制 ffmpeg 模式通常用于 GStreamer 在目标硬件或 RTSP 源上不稳定的场景。
                return self._start_command(command, rtsp_url, track_name, self.cfg.ffmpeg_publisher_cmd, "ffmpeg")
            if self.cfg.publisher_mode not in ("auto", "gstreamer"):
                raise RuntimeError(f"unsupported PUBLISHER_MODE: {self.cfg.publisher_mode}")
            if self._should_start_with_ffmpeg(command, rtsp_url):
                # auto 模式下的“先用 FFmpeg”分支，来自人工配置或历史失败缓存。
                return self._start_command(command, rtsp_url, track_name, self.cfg.ffmpeg_publisher_cmd, "ffmpeg-first")
            try:
                result = self._start_gstreamer(command, rtsp_url, track_name)
                if self.cfg.publisher_mode == "auto" and self.cfg.ffmpeg_publisher_cmd:
                    # 有些 RTSP 流能让进程启动成功，但会在几秒内因硬件编解码或 pipeline 问题退出。
                    self._watch_gstreamer_for_fallback(command, rtsp_url, track_name)
                return result
            except Exception:
                if self.cfg.publisher_mode == "gstreamer" or not self.cfg.ffmpeg_publisher_cmd:
                    raise
                # GStreamer 启动阶段失败，auto 模式立即降级 FFmpeg，并记住这个 RTSP URL。
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
        """按模板渲染自定义 publisher 命令并启动进程。

        这里同时服务 `PUBLISHER_CMD` 和 `FFMPEG_PUBLISHER_CMD`。模板会通过
        `shlex.split()` 拆成 argv，避免 shell 拼接带来的注入问题；如果确实需要
        shell 语法，应把复杂逻辑放到脚本里，再让模板调用脚本。
        """
        args = self._render_args(template, command, rtsp_url, track_name)
        if not args:
            raise RuntimeError("publisher command is empty")
        # start_new_session=True 会让子进程成为新进程组组长，后续 stop 时可以杀整个
        # 进程组，包括脚本内部再拉起的 ffmpeg/gstreamer-publisher 子进程。
        process = subprocess.Popen(args, stdout=None, stderr=None, start_new_session=True)
        print("publisher command", mode, " ".join(shlex.quote(arg) for arg in args), flush=True)
        self.processes[command.session_id] = process
        self._ensure_running(command.session_id, process)
        return "TR_" + command.session_id, track_name

    def _should_start_with_ffmpeg(self, command: StartCommand, rtsp_url: str) -> bool:
        """在 auto 模式下，对人工指定或已知 GStreamer 失败过的流直接使用 ffmpeg。

        两类流会跳过 GStreamer：

        - RTSP URL 在本进程生命周期内已经触发过 GStreamer 失败。
        - deviceId 被配置在 `PUBLISHER_FFMPEG_FIRST_DEVICE_IDS`。
        """
        if self.cfg.publisher_mode != "auto" or not self.cfg.ffmpeg_publisher_cmd:
            return False
        if rtsp_url in self.gstreamer_failed_rtsp_urls:
            return True
        return command.device_id in self.cfg.publisher_ffmpeg_first_device_ids

    def _watch_gstreamer_for_fallback(self, command: StartCommand, rtsp_url: str, track_name: str) -> None:
        """在 auto 模式下短暂观察 GStreamer 子进程，崩溃时自动切到 ffmpeg。

        `_ensure_running()` 只观察启动后的前 2 秒；有些 pipeline 会在 3-8 秒内
        因 RTSP payload、硬件解码或 EGL/display 问题退出，所以 auto 模式额外开
        一个 daemon 线程观察 `PUBLISHER_FALLBACK_WATCH_SECONDS`。
        """
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
        """如果 GStreamer 直推在观察窗口内退出，且 session 仍有效，就改用 ffmpeg 推同一路流。

        这里会再次检查 `self.processes[sessionId] is process`，防止以下竞态：

        - 用户已经 stop 了该 session。
        - 后端又下发了同 session 的新 start。
        - MQTT 断线触发了 stop_all。

        只有当前仍由这个 GStreamer 进程负责的 session，才允许自动 fallback。
        """
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
        """使用 gstreamer-publisher 默认路径发布 RTSP 到 LiveKit。

        `gstreamer-publisher` 负责连接 LiveKit 并发布 track；`GSTREAMER_PIPELINE`
        只描述 `--` 后面的媒体处理 pipeline。默认 pipeline 读取 RTSP H264，
        depay 后交给 h264parse，再由 publisher 推到 LiveKit。
        """
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
        """停止指定 session 的 publisher 进程。

        stop 通常来自 MQTT `media/video/stop`，也可能来自同 session 的重新 start。
        """
        with self.lock:
            self._stop_locked(session_id)

    def stop_all(self) -> None:
        """停止当前客户端管理的所有 publisher 进程。

        MQTT 断线或客户端退出时调用，保证机器人端不会继续向旧 LiveKit room 推流。
        """
        with self.lock:
            for session_id in list(self.processes.keys()):
                self._stop_locked(session_id)

    def _stop_locked(self, session_id: str) -> None:
        """在已持锁的情况下停止并移除一个 publisher 进程。

        优先 kill 进程组，确保脚本拉起的子进程一并结束；如果进程组 kill 失败，
        再退回 kill 主进程。
        """
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
        """短暂观察子进程是否立即退出，尽早暴露 pipeline 参数错误。

        外部 publisher 如果 token、LiveKit URL、RTSP pipeline 参数错误，通常会很快
        退出。这里等待 2 秒，如果还活着就认为“启动成功”，后续运行时异常由进程日志、
        fallback 观察线程或后端超时机制兜底。
        """
        deadline = time.monotonic() + 2
        while time.monotonic() < deadline:
            code = process.poll()
            if code is not None:
                self.processes.pop(session_id, None)
                raise RuntimeError(f"publisher exited with code {code}")
            time.sleep(0.05)

    def _render_args(self, template: str, command: StartCommand, rtsp_url: str, track_name: str) -> list[str]:
        """把命令模板拆分为 argv，并替换会话、RTSP 和 LiveKit 占位符。

        支持的占位符：

        - `{rtsp}`：最终使用的 RTSP URL。
        - `{livekitUrl}`：LiveKit 服务地址。
        - `{token}`：publisher token。
        - `{room}`：LiveKit room 名称。
        - `{track}`：客户端生成的 trackName。
        """
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
