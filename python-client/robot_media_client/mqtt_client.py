from __future__ import annotations

import json
import threading
import time
from concurrent.futures import ThreadPoolExecutor
from typing import Any, Callable
from urllib.parse import urlparse

import paho.mqtt.client as mqtt

from .config import Config
from .intercom import IntercomManager
from .model import ControlCommand, IntercomStartCommand, StartCommand, StopCommand
from .publisher import ProcessPublisher
from .rtsp import Probe
from .timeutil import isoformat


class RobotMQTTClient:
    """机器人侧 MQTT 客户端，负责订阅控制命令并上报在线、视频和对讲状态。"""

    def __init__(self, cfg: Config, probe: Probe, publisher: ProcessPublisher, intercom: IntercomManager) -> None:
        """保存运行依赖，并创建 MQTT client 与后台命令执行池。"""
        self.cfg = cfg
        self.probe = probe
        self.publisher = publisher
        self.intercom = intercom
        self.client = make_mqtt_client(cfg.client_id)
        self.executor = ThreadPoolExecutor(max_workers=8, thread_name_prefix="mqtt-command")
        self.lock = threading.RLock()
        self.subscriptions: dict[str, Callable[[bytes, str], None]] = {}
        self.last_cmds: dict[str, str] = {}
        self.state_seq = 0
        self.audio_volume = 50
        self.audio_muted = False
        self.control_mode = "MANUAL"
        self.device_state: dict[str, dict[str, object]] = {}
        self.stop_event = threading.Event()
        self.connected = threading.Event()

    def run(self) -> None:
        """连接 MQTT broker，启动网络循环、心跳线程，并阻塞直到客户端被停止。"""
        broker = parse_broker(self.cfg.mqtt_broker)
        if self.cfg.mqtt_username:
            self.client.username_pw_set(self.cfg.mqtt_username, self.cfg.mqtt_password)
        self.client.on_connect = self._on_connect
        self.client.on_disconnect = self._on_disconnect
        self.client.on_message = self._on_message
        self.client.on_subscribe = self._on_subscribe
        self.client.connect(broker.host, broker.port, keepalive=20)
        self.client.loop_start()
        heartbeat = threading.Thread(target=self._heartbeat_loop, daemon=True)
        heartbeat.start()
        try:
            while not self.stop_event.is_set():
                time.sleep(0.2)
        finally:
            self.publisher.stop_all()
            self.intercom.stop_all()
            self.online("offline")
            self.client.disconnect()
            self.client.loop_stop()
            self.executor.shutdown(wait=True, cancel_futures=False)

    def stop(self) -> None:
        """请求主循环退出。"""
        self.stop_event.set()

    def _on_connect(self, client: mqtt.Client, _userdata: object, _flags: object, rc: object, *_args: object) -> None:
        """MQTT 连接成功后订阅机器人专属控制 topic，并立即上报在线状态。"""
        if is_connect_failure(rc):
            print("mqtt connect failed", rc, flush=True)
            return
        self.connected.set()
        robot = self.cfg.robot_id
        self.subscriptions = {
            f"robot/{robot}/media/video/start": self._handle_start,
            f"robot/{robot}/media/video/stop": self._handle_stop,
            f"robot/{robot}/media/video/switch-channel": self._handle_start,
            f"robot/{robot}/media/video/intercom/start": self._handle_intercom_start,
            f"robot/{robot}/media/video/intercom/stop": self._handle_intercom_stop,
            f"robot/{robot}/control/#": self._handle_control_command,
        }
        for topic in self.subscriptions:
            result, mid = client.subscribe(topic, qos=1)
            print("mqtt subscribe requested", f"topic={topic}", f"mid={mid}", f"result={result}", flush=True)
        print("mqtt subscriptions registered", " ".join(self.subscriptions.keys()), flush=True)
        self.executor.submit(self.online, "online")

    def _on_subscribe(self, _client: mqtt.Client, _userdata: object, mid: int, reason_codes: Any = None, *_args: object) -> None:
        """打印 broker 对订阅请求的确认，便于排查 topic 未订阅成功的问题。"""
        print("mqtt subscribe ack", f"mid={mid}", f"reason={reason_codes}", flush=True)

    def _on_disconnect(self, _client: mqtt.Client, _userdata: object, *args: object) -> None:
        """MQTT 断开时立即停止本地视频和对讲资源，避免无人控制的推流残留。"""
        rc = args[-2] if len(args) >= 2 else (args[-1] if args else None)
        self.connected.clear()
        print("mqtt lost", rc, flush=True)
        self.publisher.stop_all()
        self.intercom.stop_all()

    def _on_message(self, _client: mqtt.Client, _userdata: object, message: mqtt.MQTTMessage) -> None:
        """统一接收 MQTT 消息，按订阅过滤器匹配后交给后台线程处理。"""
        payload = bytes(message.payload)
        topic = message.topic
        for topic_filter, handler in list(self.subscriptions.items()):
            if mqtt.topic_matches_sub(topic_filter, topic):
                print("mqtt message matched", f"topic={topic}", f"filter={topic_filter}", flush=True)
                # RTSP 探测和 GStreamer 启动都可能耗时；如果直接在 paho 回调线程里做，
                # publish ACK、stop 指令和其它摄像头的 start 指令都会被拖住。
                self.executor.submit(self._run_handler, handler, payload, topic)
                return
        print("mqtt message ignored", f"topic={topic}", "reason=no-handler", flush=True)

    def _run_handler(self, handler: Callable[[bytes, str], None], payload: bytes, topic: str) -> None:
        """执行一个 MQTT 命令处理器，并兜底记录未捕获异常。"""
        try:
            handler(payload, topic)
        except Exception as exc:
            print("mqtt command handler failed", topic, exc, flush=True)

    def _handle_start(self, payload: bytes, _topic: str) -> None:
        """处理 start/switch-channel 指令：探测 RTSP、启动 publisher、上报 streaming。"""
        try:
            command = StartCommand.from_json(json.loads(payload.decode()))
            if self._is_duplicate(command.session_id, command.command_id):
                return
            print("video start", command.session_id, command.channel, command.quality, flush=True)
            rtsp_url = command.rtsp_url or self.rtsp_url(command.device_id, command.quality)
            try:
                self.probe.check(rtsp_url)
            except Exception as exc:
                self.status(command.session_id, "failed", "", "", "RTSP_PROBE_FAILED", str(exc))
                return
            self.status(command.session_id, "publishing", "", "", "", "rtsp ok")
            track_sid, track_name = self.publisher.start(command, rtsp_url)
            # gstreamer-publisher 当前不会把真实 LiveKit track sid 回传给父进程；
            # 与 Go 客户端保持一致，先用 sessionId 派生稳定占位 sid，后端据此进入 STREAMING。
            self.status(command.session_id, "streaming", track_sid, track_name, "", "track published")
        except Exception as exc:
            print("video start failed", exc, flush=True)

    def _handle_stop(self, payload: bytes, _topic: str) -> None:
        """处理 stop 指令，只停止对应 session 的推流进程。"""
        try:
            command = StopCommand.from_json(json.loads(payload.decode()))
            print("video stop", command.session_id, flush=True)
            self.publisher.stop(command.session_id)
            self.status(command.session_id, "stopped", "", "", "", "stopped")
        except Exception as exc:
            print("video stop failed", exc, flush=True)

    def _handle_intercom_start(self, payload: bytes, _topic: str) -> None:
        """处理对讲开始指令，启动 LiveKit 音频桥并上报音频 track。"""
        try:
            command = IntercomStartCommand.from_json(json.loads(payload.decode()))
            if self._is_duplicate("intercom:" + command.session_id, command.command_id):
                return
            print("intercom start", command.session_id, command.room_name, flush=True)
            self.intercom_status(command.session_id, "starting", "", "", "", "starting audio bridge")
            try:
                track_sid, track_name = self.intercom.start(command)
            except Exception as exc:
                self.intercom_status(command.session_id, "failed", "", "", "INTERCOM_START_FAILED", str(exc))
                return
            self.intercom_status(command.session_id, "active", track_sid, track_name, "", "intercom audio active")
        except Exception as exc:
            print("intercom start failed", exc, flush=True)

    def _handle_intercom_stop(self, payload: bytes, _topic: str) -> None:
        """处理对讲停止指令，关闭指定 session 的音频桥。"""
        try:
            command = StopCommand.from_json(json.loads(payload.decode()))
            print("intercom stop", command.session_id, flush=True)
            self.intercom.stop(command.session_id)
            self.intercom_status(command.session_id, "stopped", "", "", "", "intercom stopped")
        except Exception as exc:
            print("intercom stop failed", exc, flush=True)

    def _handle_control_command(self, payload: bytes, topic: str) -> None:
        """处理普通设备控制指令，并把可模拟的设备状态回报给后端。"""
        try:
            raw_payload = payload.decode()
            command = ControlCommand.from_json(json.loads(raw_payload))
        except Exception as exc:
            print("control command unmarshal failed", exc, flush=True)
            return
        print(
            "equipment control command received",
            f"topic={topic}",
            f"robotId={command.robot_id}",
            f"deviceId={command.target.device_id}",
            f"deviceType={command.target.device_type}",
            f"action={command.action}",
            f"seq={command.seq}",
            f"payload={raw_payload}",
            flush=True,
        )
        if self.apply_control_command(command):
            print(
                "equipment control command applied",
                f"deviceId={command.target.device_id}",
                f"action={command.action}",
                flush=True,
            )
            try:
                self.online("online")
            except Exception as exc:
                print("equipment control state publish failed", exc, flush=True)
        else:
            print(
                "equipment control command ignored",
                f"deviceId={command.target.device_id}",
                f"deviceType={command.target.device_type}",
                f"action={command.action}",
                "reason=unsupported-or-no-state-change",
                flush=True,
            )

    def _is_duplicate(self, session_id: str, command_id: str) -> bool:
        """按 sessionId + commandId 去重，避免 broker 重投导致重复启动进程。"""
        with self.lock:
            if command_id and command_id == self.last_cmds.get(session_id):
                return True
            self.last_cmds[session_id] = command_id
            return False

    def apply_control_command(self, command: ControlCommand) -> bool:
        """应用可本地确认的设备控制状态，并触发下一次 client/status 上报。"""
        with self.lock:
            changed = False
            if command.action == "set_volume":
                self.audio_volume = clamp_int(
                    any_int(command.params.get("volumePercent"), any_int(command.params.get("volume"), self.audio_volume)),
                    0,
                    100,
                )
                self.audio_muted = False
                self.set_device_state_locked(command.target.device_id, "volume", self.audio_volume)
                self.set_device_state_locked(command.target.device_id, "volumePercent", self.audio_volume)
                self.set_device_state_locked(command.target.device_id, "muted", self.audio_muted)
                changed = True
            elif command.action == "set_mute":
                self.audio_muted = any_bool(command.params.get("mute"), any_bool(command.params.get("muted"), True))
                self.set_device_state_locked(command.target.device_id, "volume", self.audio_volume)
                self.set_device_state_locked(command.target.device_id, "volumePercent", self.audio_volume)
                self.set_device_state_locked(command.target.device_id, "muted", self.audio_muted)
                changed = True
            elif command.action == "set_safety":
                self.set_device_state_locked(
                    command.target.device_id,
                    "safetySwitchEnabled",
                    any_bool(command.params.get("safetyOn"), any_bool(command.params.get("enabled"), False)),
                )
                changed = True
            elif command.action == "control.mode.set":
                self.control_mode = normalize_control_mode(any_str(command.params.get("controlMode"), command.control_mode))
                changed = True
            elif command.action == "set_state" and command.target.device_type == "WARNING_LIGHT":
                power_on = any_bool(command.params.get("powerOn"), any_bool(command.params.get("enabled"), False))
                self.set_device_state_locked(command.target.device_id, "enabled", power_on)
                self.set_device_state_locked(command.target.device_id, "powerOn", power_on)
                changed = True
            elif command.action == "set_mode" and command.target.device_type == "WARNING_LIGHT":
                self.set_device_state_locked(command.target.device_id, "mode", any_int(command.params.get("mode"), 0))
                changed = True
            elif command.action == "ptz.auto_rotate":
                self.set_device_state_locked(command.target.device_id, "autoRotateEnabled", any_bool(command.params.get("enabled"), False))
                self.set_device_state_locked(command.target.device_id, "panSpeed", any_float(command.params.get("panSpeed"), 0.0))
                changed = True
            elif command.action == "light.vehicle.set":
                front, rear = vehicle_light_state(command.params)
                self.set_device_state_locked(command.target.device_id, "front", front)
                self.set_device_state_locked(command.target.device_id, "rear", rear)
                changed = True
            return changed

    def set_device_state_locked(self, device_id: str, key: str, value: object) -> None:
        """在已持锁状态下更新设备运行状态。"""
        if not device_id:
            return
        status = self.device_state.setdefault(device_id, {})
        old_value = status.get(key)
        status[key] = value
        if old_value != value:
            print(
                "equipment device state updated",
                f"deviceId={device_id}",
                f"{key}={value}",
                f"old={old_value}",
                flush=True,
            )

    def status(self, session_id: str, status: str, track_sid: str, track_name: str, error_code: str, message: str) -> None:
        """上报实时视频 session 状态给后端状态订阅器。"""
        self.publish(f"robot/{self.cfg.robot_id}/media/video/status", {
            "sessionId": session_id,
            "status": status,
            "trackSid": track_sid,
            "trackName": track_name,
            "errorCode": error_code,
            "message": message,
            "timestamp": isoformat(),
        })

    def intercom_status(self, session_id: str, status: str, track_sid: str, track_name: str, error_code: str, message: str) -> None:
        """上报对讲状态和机器人麦克风 track 信息。"""
        self.publish(f"robot/{self.cfg.robot_id}/media/video/intercom/status", {
            "sessionId": session_id,
            "status": status,
            "robotAudioTrackSid": track_sid,
            "robotAudioTrackName": track_name,
            "errorCode": error_code,
            "message": message,
            "timestamp": isoformat(),
        })

    def publish(self, topic: str, payload: dict[str, Any]) -> None:
        """发布 MQTT 消息并等待 QoS1 入队完成，失败时打印可排查日志。"""
        if not self.connected.is_set():
            print("mqtt publish skipped disconnected", topic, flush=True)
            return
        body = json.dumps(without_empty(payload), ensure_ascii=False, separators=(",", ":"))
        info = self.client.publish(topic, body.encode(), qos=1, retain=False)
        try:
            info.wait_for_publish(timeout=5)
        except RuntimeError as exc:
            print("mqtt publish failed", topic, exc, flush=True)
            return
        if not info.is_published():
            print("mqtt publish timeout", topic, body, flush=True)

    def online(self, status: str) -> None:
        """发布机器人在线状态、基础属性和摄像头清单。"""
        with self.lock:
            self.state_seq += 1
            state_seq = self.state_seq
            control_mode = self.control_mode
        self.publish(f"robot/{self.cfg.robot_id}/media/client/status", {
            "robotId": self.cfg.robot_id,
            "clientId": self.cfg.client_id,
            "name": self.cfg.robot_name,
            "type": self.cfg.type,
            "battery": self.cfg.battery,
            "status": status,
            "controlMode": control_mode,
            "stateSeq": state_seq,
            "missionStatus": mission_status_for_mode(control_mode),
            "navigationStatus": navigation_status_for_mode(control_mode),
            "controlOwner": None,
            "estopActive": False,
            "cameras": [
                {
                    "cameraId": camera.camera_id,
                    "deviceId": camera.device_id,
                    "groupType": camera.group_type,
                    "name": camera.name,
                    "quality": camera.quality,
                }
                for camera in self.cfg.cameras
            ],
            "devices": self.devices(),
            "timestamp": isoformat(),
        })

    def devices(self) -> list[dict[str, object]]:
        """把设备配置和当前运行状态转换为 client/status 的 devices[]。"""
        result: list[dict[str, object]] = []
        with self.lock:
            state_snapshot = {key: dict(value) for key, value in self.device_state.items()}
            audio_volume = self.audio_volume
            audio_muted = self.audio_muted
        for device in self.cfg.devices:
            status = dict(device.status or {})
            status.update(state_snapshot.get(device.device_id, {}))
            if device.device_id == "audio-control-001" or device.device_type in {"SPEAKER", "CLIENT_AUDIO", "VOLUME_CONTROL", "INTERCOM"}:
                status["volume"] = audio_volume
                status["volumePercent"] = audio_volume
                status["muted"] = audio_muted
            item: dict[str, object] = {
                "deviceId": device.device_id,
                "bindingId": device.binding_id,
                "scope": device.scope,
                "deviceType": device.device_type,
                "displayName": device.display_name,
                "onlineStatus": device.online_status,
                "controlStatus": device.control_status,
                "enabled": device.enabled,
                "actions": list(device.actions),
            }
            if device.vendor:
                item["vendor"] = device.vendor
            if device.model:
                item["model"] = device.model
            if device.risk_level:
                item["riskLevel"] = device.risk_level
            if status:
                item["status"] = status
            if device.control_profile:
                item["controlProfile"] = dict(device.control_profile)
            result.append(item)
        return result

    def rtsp_url(self, device_id: str, quality: str) -> str:
        """按 deviceId 和清晰度选择 RTSP 地址，兼容全局可见光默认地址。"""
        normalized = (quality or "").strip().lower()
        for camera in self.cfg.cameras:
            if camera.device_id == device_id or camera.camera_id == device_id:
                if normalized == "main" and camera.rtsp_main_url:
                    return camera.rtsp_main_url
                if normalized in {"sub", "auto", ""} and camera.rtsp_sub_url:
                    return camera.rtsp_sub_url
                return camera.rtsp_url
        if normalized == "main" and self.cfg.rtsp_visible_main:
            return self.cfg.rtsp_visible_main
        return self.cfg.rtsp_visible_sub

    def _heartbeat_loop(self) -> None:
        """按配置间隔持续上报在线状态，驱动后端机器人心跳。"""
        while not self.stop_event.wait(self.cfg.heartbeat_interval):
            try:
                self.online("online")
            except Exception as exc:
                print("mqtt heartbeat failed", exc, flush=True)


class Broker:
    """MQTT broker 主机和端口的简单值对象。"""

    def __init__(self, host: str, port: int) -> None:
        """保存 broker host/port。"""
        self.host = host
        self.port = port


def make_mqtt_client(client_id: str) -> mqtt.Client:
    """创建兼容 paho-mqtt 1.x 和 2.x 的 MQTT client。"""
    callback_api = getattr(mqtt, "CallbackAPIVersion", None)
    if callback_api is not None:
        return mqtt.Client(callback_api.VERSION2, client_id=client_id)
    return mqtt.Client(client_id=client_id)


def is_connect_failure(rc: object) -> bool:
    """判断 paho 1.x int rc 或 paho 2.x ReasonCode 是否表示连接失败。"""
    is_failure = getattr(rc, "is_failure", None)
    if isinstance(is_failure, bool):
        return is_failure
    try:
        return int(rc) != 0
    except (TypeError, ValueError):
        return str(rc).lower() not in {"0", "success"}


def parse_broker(raw: str) -> Broker:
    """解析 tcp://host:port 或 host:port 形式的 broker 地址。"""
    parsed = urlparse(raw)
    if parsed.scheme:
        return Broker(parsed.hostname or "localhost", parsed.port or 1883)
    host, _, port_text = raw.partition(":")
    return Broker(host or "localhost", int(port_text or "1883"))


def without_empty(payload: dict[str, Any]) -> dict[str, Any]:
    """移除空字符串和 None，保持 MQTT payload 简洁并兼容 Go 客户端格式。"""
    return {
        key: value
        for key, value in payload.items()
        if value != "" and value is not None
    }


def any_int(value: object, fallback: int) -> int:
    """把数值型参数转换成 int，无法转换时返回默认值。"""
    if isinstance(value, bool):
        return fallback
    if isinstance(value, (int, float)):
        return int(value)
    return fallback


def any_bool(value: object, fallback: bool) -> bool:
    """把布尔参数转换成 bool，无法转换时返回默认值。"""
    if isinstance(value, bool):
        return value
    return fallback


def any_str(value: object, fallback: str) -> str:
    """把字符串参数转换成 str，空值时返回默认值。"""
    if isinstance(value, str) and value.strip():
        return value
    return fallback


def normalize_control_mode(value: str) -> str:
    """规范化控制模式，只接受平台一期定义的三种模式。"""
    mode = (value or "MANUAL").strip().upper()
    if mode in {"MANUAL", "ASSISTED", "NAVIGATION"}:
        return mode
    return "MANUAL"


def mission_status_for_mode(control_mode: str) -> str:
    """根据控制模式模拟任务状态。"""
    if control_mode == "NAVIGATION":
        return "RUNNING"
    if control_mode == "ASSISTED":
        return "ASSISTED"
    return "IDLE"


def navigation_status_for_mode(control_mode: str) -> str:
    """根据控制模式模拟导航状态。"""
    return "RUNNING" if control_mode == "NAVIGATION" else "IDLE"


def any_float(value: object, fallback: float) -> float:
    """把数值型参数转换成 float，无法转换时返回默认值。"""
    if isinstance(value, bool):
        return fallback
    if isinstance(value, (int, float)):
        return float(value)
    return fallback


def copy_dict(value: object) -> dict[str, object]:
    """复制字典参数，非字典时返回空字典。"""
    if isinstance(value, dict):
        return dict(value)
    return {}


def vehicle_light_state(params: dict[str, object]) -> tuple[dict[str, object], dict[str, object]]:
    """兼容 front/rear 和 ROS msg 两种车灯命令参数。"""
    front = copy_dict(params.get("front"))
    rear = copy_dict(params.get("rear"))
    if front or rear:
        return normalize_vehicle_light_part(front), normalize_vehicle_light_part(rear)
    msg = copy_dict(params.get("msg"))
    return (
        vehicle_light_part(any_int(msg.get("front_mode"), 0), any_int(msg.get("front_custom_value"), 0)),
        vehicle_light_part(any_int(msg.get("rear_mode"), 0), any_int(msg.get("rear_custom_value"), 0)),
    )


def vehicle_light_part(mode_code: int, custom_value: int) -> dict[str, object]:
    """把 ROS mode code 转成前端使用的车灯状态。"""
    if mode_code < 0 or mode_code > 3:
        mode_code = 0
    mode = {0: "OFF", 1: "ON", 2: "BREATH", 3: "CUSTOM"}.get(mode_code, "OFF")
    return {
        "mode": mode,
        "brightness": clamp_int(custom_value, 0, 100) if mode_code == 3 else 0,
    }


def normalize_vehicle_light_part(part: dict[str, object]) -> dict[str, object]:
    """把平台通用或旧兼容车灯状态归一化为 mode/brightness。"""
    mode = any_str(part.get("mode"), "")
    if not mode:
        mode = {0: "OFF", 1: "ON", 2: "BREATH", 3: "CUSTOM"}.get(clamp_int(any_int(part.get("modeCode"), 0), 0, 3), "OFF")
    mode = normalize_vehicle_light_mode(mode)
    brightness = 0
    if mode == "CUSTOM":
        brightness = clamp_int(any_int(part.get("brightness", part.get("customValue")), 0), 0, 100)
    return {"mode": mode, "brightness": brightness}


def normalize_vehicle_light_mode(value: str) -> str:
    """规范化平台车灯模式。"""
    mode = (value or "OFF").strip().upper()
    if mode in {"ON", "BREATH", "CUSTOM"}:
        return mode
    return "OFF"


def clamp_int(value: int, minimum: int, maximum: int) -> int:
    """把整数限制到闭区间内。"""
    return max(minimum, min(maximum, value))
