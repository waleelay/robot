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
        self.client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id=cfg.client_id)
        self.executor = ThreadPoolExecutor(max_workers=8, thread_name_prefix="mqtt-command")
        self.lock = threading.RLock()
        self.last_cmds: dict[str, str] = {}
        self.state_seq = 0
        self.audio_volume = 50
        self.audio_muted = False
        self.stop_event = threading.Event()

    def run(self) -> None:
        """连接 MQTT broker，启动网络循环、心跳线程，并阻塞直到客户端被停止。"""
        broker = parse_broker(self.cfg.mqtt_broker)
        if self.cfg.mqtt_username:
            self.client.username_pw_set(self.cfg.mqtt_username, self.cfg.mqtt_password)
        self.client.on_connect = self._on_connect
        self.client.on_disconnect = self._on_disconnect
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

    def _on_connect(self, client: mqtt.Client, _userdata: object, _flags: mqtt.ConnectFlags, rc: mqtt.ReasonCode, _properties: mqtt.Properties | None) -> None:
        """MQTT 连接成功后订阅机器人专属控制 topic，并立即上报在线状态。"""
        if rc.is_failure:
            print("mqtt connect failed", rc, flush=True)
            return
        robot = self.cfg.robot_id
        subscriptions: list[tuple[str, Callable[[bytes, str], None]]] = [
            (f"robot/{robot}/media/video/start", self._handle_start),
            (f"robot/{robot}/media/video/stop", self._handle_stop),
            (f"robot/{robot}/media/video/switch-channel", self._handle_start),
            (f"robot/{robot}/media/video/intercom/start", self._handle_intercom_start),
            (f"robot/{robot}/media/video/intercom/stop", self._handle_intercom_stop),
            (f"robot/{robot}/control/#", self._handle_control_command),
        ]
        for topic, handler in subscriptions:
            client.message_callback_add(topic, self._callback(handler))
            client.subscribe(topic, qos=1)
        print("mqtt subscribed", " ".join(topic for topic, _ in subscriptions), flush=True)
        self.executor.submit(self.online, "online")

    def _on_disconnect(self, _client: mqtt.Client, _userdata: object, _flags: mqtt.DisconnectFlags, rc: mqtt.ReasonCode, _properties: mqtt.Properties | None) -> None:
        """MQTT 断开时立即停止本地视频和对讲资源，避免无人控制的推流残留。"""
        print("mqtt lost", rc, flush=True)
        self.publisher.stop_all()
        self.intercom.stop_all()

    def _callback(self, handler: Callable[[bytes, str], None]) -> Callable[[mqtt.Client, object, mqtt.MQTTMessage], None]:
        """把 MQTT 消息交给后台线程处理，保持 paho 网络线程畅通。"""
        def wrapped(_client: mqtt.Client, _userdata: object, message: mqtt.MQTTMessage) -> None:
            """复制消息内容并提交后台执行，避免回调线程被耗时任务阻塞。"""
            payload = bytes(message.payload)
            topic = message.topic

            # RTSP 探测和 GStreamer 启动都可能耗时；如果直接在 paho 回调线程里做，
            # publish ACK、stop 指令和其它摄像头的 start 指令都会被拖住。
            self.executor.submit(self._run_handler, handler, payload, topic)

        return wrapped

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
        """处理普通设备控制指令，目前 Python 客户端只落地音量/静音类状态。"""
        try:
            command = ControlCommand.from_json(json.loads(payload.decode()))
            print("equipment control command received topic=", topic, "payload=", payload.decode(), flush=True)
            if self.apply_audio_command(command):
                self.online("online")
        except Exception as exc:
            print("control command unmarshal failed", exc, flush=True)

    def _is_duplicate(self, session_id: str, command_id: str) -> bool:
        """按 sessionId + commandId 去重，避免 broker 重投导致重复启动进程。"""
        with self.lock:
            if command_id and command_id == self.last_cmds.get(session_id):
                return True
            self.last_cmds[session_id] = command_id
            return False

    def apply_audio_command(self, command: ControlCommand) -> bool:
        """应用音量、静音等客户端本地音频控制指令。"""
        if command.target.device_type not in {"CLIENT_AUDIO", "VOLUME_CONTROL", "INTERCOM"}:
            return False
        with self.lock:
            if command.action == "volume.set":
                self.audio_volume = clamp_int(any_int(command.params.get("volume"), self.audio_volume), 0, 100)
                self.audio_muted = any_bool(command.params.get("muted"), False)
            elif command.action == "volume.up":
                step = clamp_int(any_int(command.params.get("step"), 5), 1, 100)
                self.audio_volume = clamp_int(any_int(command.params.get("volume"), self.audio_volume + step), 0, 100)
                self.audio_muted = any_bool(command.params.get("muted"), False)
            elif command.action == "volume.down":
                step = clamp_int(any_int(command.params.get("step"), 5), 1, 100)
                self.audio_volume = clamp_int(any_int(command.params.get("volume"), self.audio_volume - step), 0, 100)
                self.audio_muted = any_bool(command.params.get("muted"), False)
            elif command.action == "volume.mute":
                self.audio_muted = any_bool(command.params.get("muted"), True)
                self.audio_volume = clamp_int(any_int(command.params.get("volume"), self.audio_volume), 0, 100)
            else:
                return False
            return True

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
        body = json.dumps(without_empty(payload), ensure_ascii=False, separators=(",", ":"))
        info = self.client.publish(topic, body.encode(), qos=1, retain=False)
        info.wait_for_publish(timeout=5)
        if not info.is_published():
            print("mqtt publish timeout", topic, body, flush=True)

    def online(self, status: str) -> None:
        """发布机器人在线状态、基础属性和摄像头清单。"""
        with self.lock:
            self.state_seq += 1
            state_seq = self.state_seq
        self.publish(f"robot/{self.cfg.robot_id}/media/client/status", {
            "robotId": self.cfg.robot_id,
            "clientId": self.cfg.client_id,
            "name": self.cfg.robot_name,
            "type": self.cfg.type,
            "battery": self.cfg.battery,
            "status": status,
            "controlMode": "MANUAL",
            "stateSeq": state_seq,
            "missionStatus": "IDLE",
            "navigationStatus": "IDLE",
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
            "timestamp": isoformat(),
        })

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
            self.online("online")


class Broker:
    """MQTT broker 主机和端口的简单值对象。"""

    def __init__(self, host: str, port: int) -> None:
        """保存 broker host/port。"""
        self.host = host
        self.port = port


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


def clamp_int(value: int, minimum: int, maximum: int) -> int:
    """把整数限制到闭区间内。"""
    return max(minimum, min(maximum, value))
