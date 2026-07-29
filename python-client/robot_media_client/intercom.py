"""LiveKit 对讲音频桥模块。"""

from __future__ import annotations

import asyncio
import contextlib
import logging
import queue
import subprocess
import threading
from dataclasses import dataclass
from typing import Any

from .config import Config
from .model import IntercomStartCommand
from .multifunction import MultiFunctionClient
from .opus_codec import OpusCodecError, OpusDecoder, OpusEncoder

LOGGER = logging.getLogger(__name__)

AUDIO_SAMPLE_RATE = 48000
AUDIO_CHANNELS = 1
PCM_FRAME_SAMPLES = 960
PCM_FRAME_BYTES = PCM_FRAME_SAMPLES * AUDIO_CHANNELS * 2
MULTI_BROADCAST_SAMPLE_RATE = 8000
MULTI_BROADCAST_FRAME_SAMPLES = 480
MULTI_BROADCAST_FRAME_BYTES = MULTI_BROADCAST_FRAME_SAMPLES * 2
MULTI_MONITOR_SAMPLE_RATE = 16000
MULTI_MONITOR_MAX_FRAME_SAMPLES = 960
MULTI_MONITOR_QUEUE_FRAMES = 25


@dataclass
class StartResult:
    """对讲启动成功后返回给后端的机器人音频 track 信息。"""

    track_sid: str
    track_name: str


class IntercomManager:
    """按 sessionId 管理 LiveKit 对讲音频桥。"""

    def __init__(self, cfg: Config, multi_function: MultiFunctionClient | None = None) -> None:
        """保存配置并初始化对讲 session 表。"""
        self.cfg = cfg
        self.multi_function = multi_function
        self.sessions: dict[str, IntercomSession | MultiFunctionIntercomSession] = {}
        self.lock = threading.RLock()

    def start(self, command: IntercomStartCommand) -> tuple[str, str]:
        """启动指定 session 的对讲桥；重复启动会先关闭旧桥。"""
        if not self.cfg.intercom_audio_enabled:
            raise RuntimeError("intercom audio bridge disabled by INTERCOM_AUDIO_ENABLED=false")
        with self.lock:
            self._stop_locked(command.session_id)
            if self._is_multi_function_target(command):
                if self.multi_function is None:
                    raise RuntimeError("multi-function audio adapter is unavailable")
                session = MultiFunctionIntercomSession(self.cfg, command, self.multi_function)
            else:
                session = IntercomSession(self.cfg, command)
            self.sessions[command.session_id] = session
        try:
            result = session.start()
            return result.track_sid, result.track_name
        except Exception:
            with self.lock:
                self.sessions.pop(command.session_id, None)
            session.stop()
            raise

    def _is_multi_function_target(self, command: IntercomStartCommand) -> bool:
        """按管理端组件编码选择多合一音频桥。"""
        multi_config = getattr(self.cfg, "multi_function", None)
        return bool(
            multi_config
            and getattr(multi_config, "enabled", False)
            and command.device_id == getattr(multi_config, "device_id", "")
        )

    def stop(self, session_id: str) -> None:
        """停止指定 session 的对讲桥。"""
        with self.lock:
            self._stop_locked(session_id)

    def stop_all(self) -> None:
        """停止当前客户端管理的全部对讲桥。"""
        with self.lock:
            for session_id in list(self.sessions.keys()):
                self._stop_locked(session_id)

    def _stop_locked(self, session_id: str) -> None:
        """在已持锁的情况下停止并移除一个对讲 session。"""
        session = self.sessions.pop(session_id, None)
        if session is not None:
            session.stop()


class IntercomSession:
    """一次 LiveKit 对讲会话，桥接本地 GStreamer 音频和远端 operator track。"""

    def __init__(self, cfg: Config, command: IntercomStartCommand) -> None:
        """保存启动命令，并初始化线程、事件和本地音频进程句柄。"""
        self.cfg = cfg
        self.command = command
        self.ready: queue.Queue[StartResult | BaseException] = queue.Queue(maxsize=1)
        self.loop: asyncio.AbstractEventLoop | None = None
        self.thread: threading.Thread | None = None
        self.stop_event: asyncio.Event | None = None
        self.capture: subprocess.Popen[bytes] | None = None
        self.playback: subprocess.Popen[bytes] | None = None

    def start(self) -> StartResult:
        """在后台线程中启动 asyncio 对讲流程，并等待 track 发布完成。"""
        self.thread = threading.Thread(target=self._thread_main, name=f"intercom-{self.command.session_id}", daemon=True)
        self.thread.start()
        result = self.ready.get(timeout=15)
        if isinstance(result, BaseException):
            raise result
        return result

    def stop(self) -> None:
        """通知 asyncio 循环退出，并清理采集/播放子进程。"""
        loop = self.loop
        stop_event = self.stop_event
        if loop is not None and stop_event is not None and loop.is_running():
            loop.call_soon_threadsafe(stop_event.set)
        self._terminate_process(self.capture)
        self._terminate_process(self.playback)
        if self.thread is not None and self.thread.is_alive():
            self.thread.join(timeout=5)

    def _thread_main(self) -> None:
        """对讲线程入口，把异常反馈给 start() 等待者。"""
        try:
            asyncio.run(self._run())
        except BaseException as exc:
            self._put_ready(exc)

    async def _run(self) -> None:
        """连接 LiveKit，发布机器人麦克风 track，并订阅 operator 麦克风。"""
        from livekit import rtc

        self.loop = asyncio.get_running_loop()
        self.stop_event = asyncio.Event()
        room = rtc.Room()
        playback_input: asyncio.StreamWriter | None = None
        playback_tasks: set[asyncio.Task[Any]] = set()

        def on_track_subscribed(track: Any, publication: Any, _participant: Any) -> None:
            """收到 operator 麦克风 track 后启动远端音频播放任务。"""
            publication_name = attr_or_call(publication, "name", "")
            if publication_name != "audio.operator.mic" or not is_audio_track(rtc, track):
                return
            task = asyncio.create_task(self._copy_remote_audio(track, playback_input))
            playback_tasks.add(task)
            task.add_done_callback(playback_tasks.discard)

        try:
            bind_room_event(room, "track_subscribed", on_track_subscribed)
            playback_input = await self._start_playback()
            await room.connect(self.command.livekit_url, self.command.robot_token)
            source = rtc.AudioSource(AUDIO_SAMPLE_RATE, AUDIO_CHANNELS)
            track_name = "audio.robot.mic"
            local_track = rtc.LocalAudioTrack.create_audio_track(track_name, source)
            options = make_track_options(rtc)
            if options is None:
                publication = await maybe_await(room.local_participant.publish_track(local_track))
            else:
                publication = await maybe_await(room.local_participant.publish_track(local_track, options))
            # Python LiveKit SDK 版本之间 publication 属性名不完全一致，这里做兼容读取。
            track_sid = str(attr_or_call(publication, "sid", "") or attr_or_call(publication, "SID", "") or "AT_" + self.command.session_id)
            capture_output = await self._start_capture()
            capture_task = asyncio.create_task(self._copy_capture_audio(rtc, source, capture_output))
            self._put_ready(StartResult(track_sid=track_sid, track_name=track_name))
            await self.stop_event.wait()
            capture_task.cancel()
            with contextlib.suppress(asyncio.CancelledError):
                await capture_task
        finally:
            for task in list(playback_tasks):
                task.cancel()
            if playback_tasks:
                await asyncio.gather(*playback_tasks, return_exceptions=True)
            self._terminate_process(self.capture)
            self._terminate_process(self.playback)
            with contextlib.suppress(Exception):
                await maybe_await(room.disconnect())

    async def _start_capture(self) -> asyncio.StreamReader:
        """启动本地麦克风采集 GStreamer pipeline，并返回 PCM stdout。"""
        args = self.cfg.audio_capture_pipeline.split()
        if not args:
            raise RuntimeError("AUDIO_CAPTURE_PIPELINE is empty")
        self.capture = await asyncio.create_subprocess_exec(
            self.cfg.gst_launch_path,
            "-q",
            *args,
            stdout=asyncio.subprocess.PIPE,
            stderr=None,
        )
        if self.capture.stdout is None:
            raise RuntimeError("capture pipeline stdout unavailable")
        return self.capture.stdout

    async def _start_playback(self) -> asyncio.StreamWriter:
        """启动本地扬声器播放 GStreamer pipeline，并返回 PCM stdin。"""
        args = self.cfg.audio_playback_pipeline.split()
        if not args:
            raise RuntimeError("AUDIO_PLAYBACK_PIPELINE is empty")
        self.playback = await asyncio.create_subprocess_exec(
            self.cfg.gst_launch_path,
            "-q",
            *args,
            stdin=asyncio.subprocess.PIPE,
            stdout=None,
            stderr=None,
        )
        if self.playback.stdin is None:
            raise RuntimeError("playback pipeline stdin unavailable")
        return self.playback.stdin

    async def _copy_capture_audio(self, rtc: Any, source: Any, reader: asyncio.StreamReader) -> None:
        """把本地采集到的 PCM 帧写入 LiveKit AudioSource。"""
        while True:
            frame_bytes = await reader.readexactly(PCM_FRAME_BYTES)
            frame = make_audio_frame(rtc, frame_bytes)
            await maybe_await(source.capture_frame(frame))

    async def _copy_remote_audio(self, track: Any, playback_input: asyncio.StreamWriter | None) -> None:
        """把远端 operator 音频 track 解码成 PCM 并写入本地播放 pipeline。"""
        if playback_input is None:
            return
        from livekit import rtc

        stream = rtc.AudioStream(track, sample_rate=AUDIO_SAMPLE_RATE, num_channels=AUDIO_CHANNELS)
        async for event in stream:
            frame = getattr(event, "frame", event)
            playback_input.write(audio_frame_bytes(frame))
            await playback_input.drain()

    def _put_ready(self, result: StartResult | BaseException) -> None:
        """向 start() 等待者发送启动结果；队列已满时忽略后续结果。"""
        with contextlib.suppress(queue.Full):
            self.ready.put_nowait(result)

    @staticmethod
    def _terminate_process(process: subprocess.Popen[bytes] | Any | None) -> None:
        """尽力终止一个 GStreamer 子进程。"""
        if process is None:
            return
        with contextlib.suppress(Exception):
            if process.returncode is None:
                process.terminate()
        with contextlib.suppress(Exception):
            process.kill()


class MultiFunctionIntercomSession:
    """桥接 LiveKit PCM 与多合一设备的裸 Opus 音频帧。"""

    def __init__(
            self,
            cfg: Config,
            command: IntercomStartCommand,
            adapter: MultiFunctionClient) -> None:
        self.cfg = cfg
        self.command = command
        self.adapter = adapter
        self.ready: queue.Queue[StartResult | BaseException] = queue.Queue(maxsize=1)
        self.loop: asyncio.AbstractEventLoop | None = None
        self.thread: threading.Thread | None = None
        self.stop_event: asyncio.Event | None = None
        self.monitor_queue: asyncio.Queue[bytes] | None = None
        self.monitor_decode_errors = 0

    def start(self) -> StartResult:
        """在后台线程中建立多合一设备 LiveKit 音频桥。"""
        self.thread = threading.Thread(
            target=self._thread_main,
            name=f"multi-function-intercom-{self.command.session_id}",
            daemon=True,
        )
        self.thread.start()
        result = self.ready.get(timeout=15)
        if isinstance(result, BaseException):
            raise result
        return result

    def stop(self) -> None:
        """停止 LiveKit 会话并解除设备收音帧回调。"""
        self.adapter.set_monitor_frame_handler(None)
        loop = self.loop
        stop_event = self.stop_event
        if loop is not None and stop_event is not None and loop.is_running():
            loop.call_soon_threadsafe(stop_event.set)
        if self.thread is not None and self.thread.is_alive():
            self.thread.join(timeout=5)

    def _thread_main(self) -> None:
        try:
            asyncio.run(self._run())
        except BaseException as exc:
            self._put_ready(exc)

    async def _run(self) -> None:
        """连接 LiveKit，发布设备收音并订阅操作端麦克风。"""
        from livekit import rtc

        self.loop = asyncio.get_running_loop()
        self.stop_event = asyncio.Event()
        self.monitor_queue = asyncio.Queue(maxsize=MULTI_MONITOR_QUEUE_FRAMES)
        encoder = OpusEncoder(MULTI_BROADCAST_SAMPLE_RATE)
        decoder = OpusDecoder(MULTI_MONITOR_SAMPLE_RATE)
        room = rtc.Room()
        source = rtc.AudioSource(AUDIO_SAMPLE_RATE, AUDIO_CHANNELS)
        playback_tasks: set[asyncio.Task[Any]] = set()

        def on_track_subscribed(track: Any, publication: Any, _participant: Any) -> None:
            publication_name = attr_or_call(publication, "name", "")
            if publication_name != "audio.operator.mic" or not is_audio_track(rtc, track):
                return
            task = asyncio.create_task(self._copy_operator_audio(rtc, track, encoder))
            playback_tasks.add(task)
            task.add_done_callback(playback_tasks.discard)

        def on_monitor_frame(packet: bytes) -> None:
            loop = self.loop
            monitor_queue = self.monitor_queue
            if loop is None or monitor_queue is None or not loop.is_running():
                return
            loop.call_soon_threadsafe(self._enqueue_monitor_packet, monitor_queue, packet)

        monitor_task: asyncio.Task[Any] | None = None
        try:
            bind_room_event(room, "track_subscribed", on_track_subscribed)
            self.adapter.set_monitor_frame_handler(on_monitor_frame)
            await room.connect(self.command.livekit_url, self.command.robot_token)
            track_name = "audio.robot.mic"
            local_track = rtc.LocalAudioTrack.create_audio_track(track_name, source)
            options = make_track_options(rtc)
            if options is None:
                publication = await maybe_await(room.local_participant.publish_track(local_track))
            else:
                publication = await maybe_await(room.local_participant.publish_track(local_track, options))
            track_sid = str(
                attr_or_call(publication, "sid", "")
                or attr_or_call(publication, "SID", "")
                or "AT_" + self.command.session_id
            )
            monitor_task = asyncio.create_task(self._copy_monitor_audio(rtc, source, decoder))
            self._put_ready(StartResult(track_sid=track_sid, track_name=track_name))
            await self.stop_event.wait()
        finally:
            self.adapter.set_monitor_frame_handler(None)
            if monitor_task is not None:
                monitor_task.cancel()
                with contextlib.suppress(asyncio.CancelledError):
                    await monitor_task
            for task in list(playback_tasks):
                task.cancel()
            if playback_tasks:
                await asyncio.gather(*playback_tasks, return_exceptions=True)
            encoder.close()
            decoder.close()
            with contextlib.suppress(Exception):
                await maybe_await(room.disconnect())

    async def _copy_operator_audio(self, rtc: Any, track: Any, encoder: OpusEncoder) -> None:
        """把浏览器麦克风音频转为 8kHz/60ms Opus 帧写入设备。"""
        stream = rtc.AudioStream(track, sample_rate=AUDIO_SAMPLE_RATE, num_channels=AUDIO_CHANNELS)
        resampler = rtc.AudioResampler(AUDIO_SAMPLE_RATE, MULTI_BROADCAST_SAMPLE_RATE, num_channels=1)
        pcm_buffer = bytearray()
        async for event in stream:
            frame = getattr(event, "frame", event)
            for output_frame in resampler.push(frame):
                pcm_buffer.extend(audio_frame_bytes(output_frame))
            while len(pcm_buffer) >= MULTI_BROADCAST_FRAME_BYTES:
                pcm = bytes(pcm_buffer[:MULTI_BROADCAST_FRAME_BYTES])
                del pcm_buffer[:MULTI_BROADCAST_FRAME_BYTES]
                packet = encoder.encode(pcm, MULTI_BROADCAST_FRAME_SAMPLES)
                try:
                    await asyncio.to_thread(self.adapter.write_broadcast_opus_frame, packet)
                except RuntimeError as exc:
                    if str(exc) != "broadcast is not active":
                        raise

    async def _copy_monitor_audio(self, rtc: Any, source: Any, decoder: OpusDecoder) -> None:
        """把设备 16kHz Opus 收音帧解码并发布到 LiveKit。"""
        monitor_queue = self.monitor_queue
        if monitor_queue is None:
            return
        resampler = rtc.AudioResampler(MULTI_MONITOR_SAMPLE_RATE, AUDIO_SAMPLE_RATE, num_channels=1)
        while True:
            packet = await monitor_queue.get()
            try:
                pcm, samples = decoder.decode(packet, MULTI_MONITOR_MAX_FRAME_SAMPLES)
            except (OpusCodecError, ValueError) as exc:
                self.monitor_decode_errors += 1
                if self.monitor_decode_errors == 1 or self.monitor_decode_errors % 100 == 0:
                    LOGGER.warning(
                        "drop invalid multi-function monitor frame sessionId=%s errors=%d size=%d error=%s",
                        self.command.session_id,
                        self.monitor_decode_errors,
                        len(packet),
                        exc,
                    )
                continue
            input_frame = make_audio_frame_with_rate(
                rtc,
                pcm,
                MULTI_MONITOR_SAMPLE_RATE,
                AUDIO_CHANNELS,
                samples,
            )
            for output_frame in resampler.push(input_frame):
                await maybe_await(source.capture_frame(output_frame))

    @staticmethod
    def _enqueue_monitor_packet(monitor_queue: asyncio.Queue[bytes], packet: bytes) -> None:
        """设备收音突发时丢弃最旧帧，避免不断累积延迟。"""
        if monitor_queue.full():
            with contextlib.suppress(asyncio.QueueEmpty):
                monitor_queue.get_nowait()
        with contextlib.suppress(asyncio.QueueFull):
            monitor_queue.put_nowait(bytes(packet))

    def _put_ready(self, result: StartResult | BaseException) -> None:
        with contextlib.suppress(queue.Full):
            self.ready.put_nowait(result)


def bind_room_event(room: Any, event_name: str, callback: Any) -> None:
    """兼容不同 LiveKit SDK 的事件绑定写法。"""
    binder = getattr(room, "on", None)
    if binder is None:
        raise RuntimeError("LiveKit Room does not expose event binding")
    try:
        result = binder(event_name, callback)
    except TypeError:
        result = binder(event_name)
    if callable(result) and result is not callback:
        result(callback)


def is_audio_track(rtc: Any, track: Any) -> bool:
    """判断 LiveKit track 是否为音频 track，兼容 enum 命名差异。"""
    kind = attr_or_call(track, "kind", None)
    if kind is None:
        return True
    audio_kinds = [
        getattr(getattr(rtc, "TrackKind", object), "KIND_AUDIO", None),
        getattr(getattr(rtc, "TrackKind", object), "AUDIO", None),
        enum_value(getattr(rtc, "TrackKind", None), "KIND_AUDIO"),
    ]
    return kind in audio_kinds or str(kind).lower().endswith("audio")


def make_track_options(rtc: Any) -> Any:
    """创建音频发布选项，尽量标记 track source 为麦克风。"""
    options_cls = getattr(rtc, "TrackPublishOptions", None)
    if options_cls is None:
        return None
    source = enum_value(getattr(rtc, "TrackSource", None), "SOURCE_MICROPHONE")
    if source is None:
        source = getattr(getattr(rtc, "TrackSource", object), "SOURCE_MICROPHONE", None)
    if source is None:
        source = getattr(getattr(rtc, "TrackSource", object), "MICROPHONE", None)
    try:
        return options_cls(source=source)
    except TypeError:
        options = options_cls()
        if source is not None:
            setattr(options, "source", source)
        return options


def make_audio_frame(rtc: Any, data: bytes) -> Any:
    """按当前 SDK 构造 AudioFrame。"""
    return make_audio_frame_with_rate(
        rtc,
        data,
        AUDIO_SAMPLE_RATE,
        AUDIO_CHANNELS,
        PCM_FRAME_SAMPLES,
    )


def make_audio_frame_with_rate(
        rtc: Any,
        data: bytes,
        sample_rate: int,
        channels: int,
        samples_per_channel: int) -> Any:
    """按指定采样率构造 LiveKit AudioFrame。"""
    frame_cls = rtc.AudioFrame
    try:
        return frame_cls(
            data=data,
            sample_rate=sample_rate,
            num_channels=channels,
            samples_per_channel=samples_per_channel,
        )
    except TypeError:
        return frame_cls(data, sample_rate, channels, samples_per_channel)


def audio_frame_bytes(frame: Any) -> bytes:
    """把 SDK 返回的音频帧数据转换为 bytes。"""
    data = getattr(frame, "data", b"")
    if isinstance(data, bytes):
        return data
    if isinstance(data, bytearray):
        return bytes(data)
    return memoryview(data).tobytes()


def enum_value(enum: Any, name: str) -> Any:
    """从 protobuf 风格 enum 上读取指定名字的值。"""
    if enum is None:
        return None
    value_func = getattr(enum, "Value", None)
    if callable(value_func):
        with contextlib.suppress(Exception):
            return value_func(name)
    return None


async def maybe_await(value: Any) -> Any:
    """兼容同步返回值和 awaitable 返回值。"""
    if hasattr(value, "__await__"):
        return await value
    return value


def attr_or_call(obj: Any, name: str, fallback: Any) -> Any:
    """读取属性；如果属性是无参方法则调用它。"""
    value = getattr(obj, name, fallback)
    if callable(value):
        with contextlib.suppress(TypeError):
            return value()
    return value
