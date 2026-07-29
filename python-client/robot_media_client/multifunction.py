"""多合一喊话、收音、文件播放和照明设备的真实 TCP/HTTP 适配器。"""

from __future__ import annotations

import json
import math
import socket
import threading
import time
from pathlib import Path
from typing import Callable

import requests

from .config import MultiFunctionConfig


class MultiFunctionClient:
    """维护 8519 长连接，并把平台 action 转成设备原始协议。"""

    def __init__(self, cfg: MultiFunctionConfig) -> None:
        self.cfg = cfg
        self.stop_event = threading.Event()
        self.connected = threading.Event()
        self.connection_lock = threading.RLock()
        self.write_lock = threading.Lock()
        self.state_lock = threading.RLock()
        self.connection: socket.socket | None = None
        self.state: dict[str, object] = {
            "connected": False,
            "audioFiles": [],
            "audioSession": {
                "mediaSessionId": "",
                "state": "IDLE",
                "broadcastActive": False,
                "monitorActive": False,
                "monitorSuppressed": False,
                "monitorTrackSid": "",
                "lastError": None,
            },
        }
        self.state_handler: Callable[[dict[str, object]], None] | None = None
        self.monitor_handler: Callable[[bytes], None] | None = None
        self.threads: list[threading.Thread] = []

    def start(self) -> None:
        """启动设备长连接与灯光保活线程。"""
        if not self.cfg.enabled:
            self._update_state({"connected": False, "lastError": "multi-function device adapter is disabled"})
            return
        if self.threads:
            return
        self.threads = [threading.Thread(target=self._connection_loop, name="multi-function-tcp", daemon=True)]
        if self.cfg.keepalive_enabled:
            self.threads.append(threading.Thread(target=self._keepalive_loop, name="multi-function-keepalive", daemon=True))
        for thread in self.threads:
            thread.start()

    def close(self) -> None:
        """停止后台线程并关闭设备连接。"""
        self.stop_event.set()
        self._close_connection()
        for thread in self.threads:
            thread.join(timeout=self.cfg.dial_timeout + 1)

    def set_state_handler(self, handler: Callable[[dict[str, object]], None] | None) -> None:
        """注册设备状态变化回调。"""
        with self.state_lock:
            self.state_handler = handler

    def set_monitor_frame_handler(self, handler: Callable[[bytes], None] | None) -> None:
        """注册设备收音 Opus 帧回调。"""
        with self.state_lock:
            self.monitor_handler = handler

    def snapshot(self) -> dict[str, object]:
        """返回不会被后台线程继续修改的状态副本。"""
        with self.state_lock:
            return json.loads(json.dumps(self.state, ensure_ascii=False))

    def execute(self, action: str, params: dict[str, object]) -> dict[str, object]:
        """执行一个平台 action，成功返回设备状态快照。"""
        if not self.cfg.enabled:
            raise RuntimeError("multi-function device adapter is disabled")
        try:
            if action == "set_volume":
                self._send_control(volume_command(required_percent(params, "volumePercent")))
            elif action == "start_broadcast":
                self._wait_connection()
                self._update_audio_session(str(params.get("mediaSessionId") or ""), {"broadcastActive": True})
            elif action == "stop_broadcast":
                self._send_control(b"[11]")
                self._update_audio_session("", {"broadcastActive": False})
            elif action == "start_monitor":
                self._send_control(b"[40]")
                self._update_audio_session(str(params.get("mediaSessionId") or ""), {"monitorActive": True})
            elif action == "stop_monitor":
                self._send_control(b"[41]")
                self._update_audio_session("", {"monitorActive": False, "monitorSuppressed": False})
            elif action == "set_monitor_suppressed":
                suppressed = strict_bool(params.get("suppressed"), "suppressed")
                self._send_control(b"[45]" if suppressed else b"[46]")
                self._update_audio_session("", {"monitorSuppressed": suppressed})
            elif action == "play_tts":
                text = str(params.get("text") or "").strip()
                voice = str(params.get("voice") or "").strip().upper()
                if not text:
                    raise ValueError("text is required")
                if voice not in {"MALE", "FEMALE"}:
                    raise ValueError("voice must be MALE or FEMALE")
                prefix = "[32]" if bool(params.get("loop", False)) else "[31]"
                voice_code = "1" if voice == "FEMALE" else "0"
                self._send_control((prefix + voice_code + text).encode())
            elif action == "stop_tts":
                self._send_control(b"[17]")
            elif action == "list_audio_files":
                self._update_state({
                    "audioFiles": self.list_audio_files(),
                    "audioFilesUpdatedAt": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
                })
            elif action == "play_audio_file":
                file_name = validate_file_name(str(params.get("fileName") or ""))
                loop_flag = "1" if bool(params.get("loop", False)) else "0"
                self._send_control(("[12]" + loop_flag + file_name).encode())
            elif action == "stop_audio_file":
                self._send_control(b"[13]")
            elif action == "delete_audio_file":
                self.delete_audio_file(str(params.get("fileName") or ""))
                self._update_state({
                    "audioFiles": self.list_audio_files(),
                    "audioFilesUpdatedAt": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
                })
            elif action == "play_alarm":
                self._send_control(b"[18]")
            elif action == "stop_alarm":
                self._send_control(b"[19]")
            elif action == "light.set":
                self._set_light(params)
            elif action == "set_speaker_tilt":
                self._set_speaker_tilt(required_percent(params, "positionPercent"))
            elif action == "set_light_tilt":
                percent = required_percent(params, "positionPercent")
                self._send_control(build_light_frame(0x09, bytes([0xFF, percent_to_range(percent, 100, 200)])))
            else:
                raise ValueError(f"unsupported multi-function action: {action}")
        except Exception as exc:
            self._update_state({"lastError": str(exc)})
            raise
        self._update_state({
            "lastError": None,
            "lastCommand": action,
            "lastCommandAt": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
        })
        return self.snapshot()

    def write_broadcast_opus_frame(self, frame: bytes) -> None:
        """向 8519 写入一帧 8kHz 单声道 Opus 喊话数据。"""
        if not frame:
            raise ValueError("empty broadcast Opus frame")
        session = self.snapshot().get("audioSession")
        if not isinstance(session, dict) or not session.get("broadcastActive"):
            raise RuntimeError("broadcast is not active")
        self._send_control(b"[10]" + frame)

    def list_audio_files(self) -> list[str]:
        """查询设备 HTTP 文件清单。"""
        response = requests.get(self._http_url("/fetch-files"), timeout=self.cfg.http_timeout)
        response.raise_for_status()
        data = response.json()
        ensure_device_http_success(data)
        values = data.get("data", []) if isinstance(data, dict) else []
        return unique_file_names(values)

    def delete_audio_file(self, file_name: str) -> None:
        """删除设备内音频文件。"""
        file_name = validate_file_name(file_name)
        response = requests.post(
            self._http_url("/del-file"),
            data={"filename": file_name},
            timeout=self.cfg.http_timeout,
        )
        response.raise_for_status()
        ensure_optional_device_http_success(response)

    def upload_audio_file(self, path: str) -> None:
        """将音频文件上传到设备 HTTP 服务。"""
        source = Path(path)
        with source.open("rb") as stream:
            response = requests.post(
                self._http_url("/upload-file"),
                files={"file": (source.name, stream)},
                timeout=self.cfg.http_timeout,
            )
        response.raise_for_status()
        ensure_optional_device_http_success(response)

    def _connection_loop(self) -> None:
        backoff = 1.0
        while not self.stop_event.is_set():
            try:
                connection = socket.create_connection(
                    (self.cfg.host, self.cfg.control_port),
                    timeout=self.cfg.dial_timeout,
                )
                connection.settimeout(1)
                with self.connection_lock:
                    self.connection = connection
                    self.connected.set()
                self._update_state({"connected": True, "lastError": None})
                backoff = 1.0
                parser = StreamParser()
                while not self.stop_event.is_set():
                    try:
                        chunk = connection.recv(16384)
                    except socket.timeout:
                        continue
                    if not chunk:
                        break
                    for kind, payload in parser.feed(chunk):
                        self._handle_event(kind, payload)
            except OSError as exc:
                if not self.stop_event.is_set():
                    self._update_state({"connected": False, "lastError": str(exc)})
            finally:
                self._close_connection()
            if self.stop_event.wait(backoff):
                return
            backoff = min(backoff * 2, 5)

    def _handle_event(self, kind: str, payload: bytes) -> None:
        if kind == "status":
            state = parse_device_status(payload)
            state["observedAt"] = time.strftime("%Y-%m-%dT%H:%M:%S%z")
            self._update_state(state)
        elif kind == "volume":
            self._update_state({"volumePercent": max(0, min(int(payload.decode(), 16), 100))})
        elif kind == "tts_finished":
            self._update_state({"lastTtsCompletedAt": time.strftime("%Y-%m-%dT%H:%M:%S%z")})
        elif kind == "audio_finished":
            self._update_state({"lastAudioFinishedAt": time.strftime("%Y-%m-%dT%H:%M:%S%z")})
        elif kind == "monitor_opus":
            with self.state_lock:
                handler = self.monitor_handler
            if handler is not None:
                handler(bytes(payload))

    def _keepalive_loop(self) -> None:
        interval = max(self.cfg.keepalive_interval, 0.1)
        while not self.stop_event.wait(interval):
            if self.connected.is_set():
                try:
                    self._send_control(build_light_frame(0x04))
                except OSError:
                    pass

    def _wait_connection(self) -> socket.socket:
        if not self.connected.wait(self.cfg.dial_timeout):
            raise TimeoutError("multi-function control connection timeout")
        with self.connection_lock:
            if self.connection is None:
                raise ConnectionError("multi-function control connection is unavailable")
            return self.connection

    def _send_control(self, payload: bytes) -> None:
        with self.write_lock:
            connection = self._wait_connection()
            connection.settimeout(self.cfg.write_timeout)
            try:
                connection.sendall(payload)
            except OSError:
                self._close_connection(connection)
                raise

    def _set_speaker_tilt(self, percent: int) -> None:
        with socket.create_connection(
            (self.cfg.host, self.cfg.tilt_port),
            timeout=self.cfg.dial_timeout,
        ) as connection:
            connection.settimeout(self.cfg.write_timeout)
            connection.sendall(bytes([0x8D, percent_to_range(percent, 80, 220)]))

    def _set_light(self, params: dict[str, object]) -> None:
        known = {"enabled", "brightness", "strobeEnabled", "redBlueMode"}
        if not known.intersection(params):
            raise ValueError("light.set requires at least one parameter")
        if "enabled" in params:
            enabled = strict_bool(params["enabled"], "enabled")
            if not enabled:
                self._send_control(build_light_frame(0x03, b"\x00"))
            self._send_control(build_light_frame(0x01, b"\x01" if enabled else b"\x00"))
        if "brightness" in params:
            brightness = required_percent(params, "brightness")
            self._send_control(build_light_frame(0x02, bytes([round_half_up(brightness * 30 / 100)])))
        if "strobeEnabled" in params:
            enabled = strict_bool(params["strobeEnabled"], "strobeEnabled")
            self._send_control(build_light_frame(0x03, b"\x01" if enabled else b"\x00"))
            if not enabled:
                self._send_control(build_light_frame(0x01, b"\x00"))
        if "redBlueMode" in params:
            mode = strict_int(params["redBlueMode"], "redBlueMode")
            if mode < 0 or mode > 16:
                raise ValueError("redBlueMode must be between 0 and 16")
            self._send_control(build_light_frame(0x07, bytes([mode])))

    def _update_audio_session(self, media_session_id: str, values: dict[str, object]) -> None:
        with self.state_lock:
            session = dict(self.state.get("audioSession") or {})
            if media_session_id:
                session["mediaSessionId"] = media_session_id
            session.update(values)
            if session.get("broadcastActive") or session.get("monitorActive"):
                session["state"] = "ACTIVE"
            else:
                session["state"] = "IDLE"
                session["mediaSessionId"] = ""
            self.state["audioSession"] = session
            snapshot = self.snapshot()
            handler = self.state_handler
        if handler is not None:
            handler(snapshot)

    def _update_state(self, values: dict[str, object]) -> None:
        with self.state_lock:
            self.state.update(values)
            snapshot = self.snapshot()
            handler = self.state_handler
        if handler is not None:
            handler(snapshot)

    def _close_connection(self, expected: socket.socket | None = None) -> None:
        with self.connection_lock:
            if expected is not None and self.connection is not expected:
                return
            connection = self.connection
            self.connection = None
            self.connected.clear()
        if connection is not None:
            try:
                connection.close()
            except OSError:
                pass
        self._update_state({"connected": False})

    def _http_url(self, path: str) -> str:
        return f"http://{self.cfg.host}:{self.cfg.http_port}{path}"


class StreamParser:
    """按实测标记拆分 8519 上的状态、完成通知和收音 Opus 数据。"""

    ASCII_MARKERS = (b"[99]", b"[14]", b"[30]", b"[39]", b"[40]")
    LIGHT_MESSAGE_IDS = {0x01, 0x02, 0x03, 0x04, 0x07, 0x09}

    def __init__(self) -> None:
        self.buffer = bytearray()

    def feed(self, chunk: bytes) -> list[tuple[str, bytes]]:
        self.buffer.extend(chunk)
        events: list[tuple[str, bytes]] = []
        while self.buffer:
            data = bytes(self.buffer)
            if data.startswith(b"[99]"):
                end = quoted_json_end(data[4:])
                if end < 0:
                    break
                events.append(("status", data[4:4 + end]))
                del self.buffer[:4 + end]
            elif data.startswith(b"[14]"):
                payload, consumed = volume_payload(data[4:])
                if consumed == 0:
                    break
                events.append(("volume", payload))
                del self.buffer[:4 + consumed]
            elif data.startswith(b"[30]"):
                events.append(("tts_finished", b""))
                del self.buffer[:4]
            elif data.startswith(b"[39]"):
                events.append(("audio_finished", b""))
                del self.buffer[:4]
            elif data.startswith(b"[40]"):
                index = next_marker(data, 4)
                if index < 0:
                    break
                if index > 4:
                    events.append(("monitor_opus", data[4:index]))
                del self.buffer[:index]
            elif data[0] == 0x8D:
                if len(data) < 4:
                    break
                frame_length = data[1] + 4
                if data[1] > 2 or data[2] not in self.LIGHT_MESSAGE_IDS:
                    del self.buffer[:1]
                    continue
                if len(data) < frame_length:
                    break
                frame = data[:frame_length]
                if crc8_maxim(frame[1:-1]) != frame[-1]:
                    del self.buffer[:1]
                    continue
                events.append(("light_frame", frame))
                del self.buffer[:frame_length]
            else:
                index = next_marker(data, 1)
                if index < 0:
                    if len(self.buffer) > 8:
                        del self.buffer[:-8]
                    break
                del self.buffer[:index]
        return events


def crc8_maxim(data: bytes) -> int:
    """计算灯光协议使用的 CRC-8/MAXIM。"""
    crc = 0
    for value in data:
        crc ^= value
        for _ in range(8):
            crc = (crc >> 1) ^ 0x8C if crc & 1 else crc >> 1
    return crc


def build_light_frame(message_id: int, payload: bytes = b"") -> bytes:
    """构造 8D + len + msgId + payload + CRC。"""
    body = bytes([len(payload), message_id]) + payload
    return b"\x8D" + body + bytes([crc8_maxim(body)])


def volume_command(percent: int) -> bytes:
    return f"[14]{max(0, min(percent, 100)):02x}".encode()


def percent_to_range(percent: int, minimum: int, maximum: int) -> int:
    return minimum + round_half_up(max(0, min(percent, 100)) * (maximum - minimum) / 100)


def round_half_up(value: float) -> int:
    return int(math.floor(value + 0.5))


def strict_int(value: object, field: str) -> int:
    if isinstance(value, bool) or not isinstance(value, (int, float)) or int(value) != value:
        raise ValueError(f"{field} must be an integer")
    return int(value)


def required_percent(params: dict[str, object], field: str) -> int:
    if field not in params:
        raise ValueError(f"{field} is required")
    value = strict_int(params[field], field)
    if value < 0 or value > 100:
        raise ValueError(f"{field} must be between 0 and 100")
    return value


def strict_bool(value: object, field: str) -> bool:
    if not isinstance(value, bool):
        raise ValueError(f"{field} must be boolean")
    return value


def validate_file_name(file_name: str) -> str:
    value = file_name.strip()
    if not value:
        raise ValueError("fileName is required")
    if Path(value).name != value or ".." in value:
        raise ValueError("fileName must not contain a path")
    return value


def ensure_device_http_success(data: object) -> None:
    if isinstance(data, dict) and "code" in data and data["code"] not in {0, "0"}:
        raise RuntimeError(f"device HTTP request failed: {data}")


def ensure_optional_device_http_success(response: requests.Response) -> None:
    """设备未声明响应体时只依赖 HTTP 状态；JSON code 存在时再校验。"""
    if not response.content.strip():
        return
    try:
        data = response.json()
    except requests.exceptions.JSONDecodeError:
        return
    ensure_device_http_success(data)


def unique_file_names(value: object) -> list[str]:
    result: list[str] = []
    if isinstance(value, list):
        for item in value:
            if isinstance(item, str) and item.strip() and item.strip() not in result:
                result.append(item.strip())
            elif isinstance(item, dict):
                for key in ("filename", "fileName", "name"):
                    name = item.get(key)
                    if isinstance(name, str) and name.strip() and name.strip() not in result:
                        result.append(name.strip())
                        break
    return result


def volume_payload(data: bytes) -> tuple[bytes, int]:
    if data.startswith(b'"'):
        end = data.find(b'"', 1)
        if end < 0:
            return b"", 0
        payload = data[1:end]
        if len(payload) not in {1, 2} or any(value not in b"0123456789abcdefABCDEF" for value in payload):
            return b"", end + 1
        return payload, end + 1
    return (data[:2], 2) if len(data) >= 2 else (b"", 0)


def next_marker(data: bytes, start: int) -> int:
    indexes = [data.find(marker, start) for marker in StreamParser.ASCII_MARKERS]
    light_index = next_light_frame(data, start)
    if light_index >= 0:
        indexes.append(light_index)
    indexes = [index for index in indexes if index >= 0]
    return min(indexes) if indexes else -1


def next_light_frame(data: bytes, start: int) -> int:
    """只把校验正确的完整 8D 报文作为边界，避免切断二进制 Opus。"""
    index = data.find(b"\x8D", start)
    while index >= 0:
        if len(data) - index >= 4:
            frame_length = data[index + 1] + 4
            if (
                    data[index + 1] <= 2
                    and data[index + 2] in StreamParser.LIGHT_MESSAGE_IDS
                    and len(data) - index >= frame_length
                    and crc8_maxim(data[index + 1:index + frame_length - 1]) == data[index + frame_length - 1]):
                return index
        index = data.find(b"\x8D", index + 1)
    return -1


def quoted_json_end(data: bytes) -> int:
    start = data.find(b"{")
    if start < 0:
        return -1
    depth = 0
    in_string = False
    escaped = False
    for index in range(start, len(data)):
        value = data[index]
        if in_string:
            if escaped:
                escaped = False
            elif value == 0x5C:
                escaped = True
            elif value == 0x22:
                in_string = False
        elif value == 0x22:
            in_string = True
        elif value == 0x7B:
            depth += 1
        elif value == 0x7D:
            depth -= 1
            if depth == 0:
                end = index + 1
                return end + 1 if end < len(data) and data[end] == 0x22 else end
    return -1


def parse_device_status(payload: bytes) -> dict[str, object]:
    value = payload.strip()
    if value.startswith(b'"') and value.endswith(b'"'):
        value = value[1:-1]
    raw = json.loads(value.decode())
    state: dict[str, object] = {"connected": True}
    if "volume_real" in raw:
        state["volumePercent"] = max(0, min(int(float(raw["volume_real"])), 100))
    if "volume_limit" in raw:
        state["volumeLimitPercent"] = max(0, min(int(float(raw["volume_limit"])), 100))
    if "temperature" in raw:
        state["temperatureC"] = float(raw["temperature"])
    return state
