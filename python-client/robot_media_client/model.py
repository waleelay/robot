"""MQTT 消息模型。"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime
from typing import Any

from .timeutil import parse_time


@dataclass
class StartCommand:
    """后端通过 MQTT 下发的实时视频 start/switch 指令。"""

    command_id: str = ""
    session_id: str = ""
    robot_id: str = ""
    device_id: str = ""
    channel: str = ""
    quality: str = ""
    livekit_url: str = ""
    room_name: str = ""
    publisher_token: str = ""
    publish_identity: str = ""
    rtsp_url: str = ""
    expires_at: datetime | None = None

    @classmethod
    def from_json(cls, data: dict[str, Any]) -> "StartCommand":
        """从后端 JSON payload 构造 start 指令对象。"""
        return cls(
            command_id=str(data.get("commandId") or ""),
            session_id=str(data.get("sessionId") or ""),
            robot_id=str(data.get("robotId") or ""),
            device_id=str(data.get("deviceId") or ""),
            channel=str(data.get("channel") or ""),
            quality=str(data.get("quality") or ""),
            livekit_url=str(data.get("livekitUrl") or ""),
            room_name=str(data.get("roomName") or ""),
            publisher_token=str(data.get("publisherToken") or ""),
            publish_identity=str(data.get("publishIdentity") or ""),
            rtsp_url=str(data.get("rtspUrl") or ""),
            expires_at=parse_time(data.get("expiresAt")),
        )


@dataclass
class StopCommand:
    """后端通过 MQTT 下发的停止视频或停止对讲指令。"""

    command_id: str = ""
    session_id: str = ""
    room_name: str = ""

    @classmethod
    def from_json(cls, data: dict[str, Any]) -> "StopCommand":
        """从后端 JSON payload 构造 stop 指令对象。"""
        return cls(
            command_id=str(data.get("commandId") or ""),
            session_id=str(data.get("sessionId") or ""),
            room_name=str(data.get("roomName") or ""),
        )


@dataclass
class IntercomStartCommand:
    """后端通过 MQTT 下发的对讲音频桥启动指令。"""

    command_id: str = ""
    session_id: str = ""
    robot_id: str = ""
    device_id: str = ""
    room_name: str = ""
    livekit_url: str = ""
    robot_token: str = ""
    publish_audio: bool = False
    subscribe_operator_audio: bool = False
    publish_video: bool = False
    expires_at: datetime | None = None

    @classmethod
    def from_json(cls, data: dict[str, Any]) -> "IntercomStartCommand":
        """从后端 JSON payload 构造对讲 start 指令对象。"""
        return cls(
            command_id=str(data.get("commandId") or ""),
            session_id=str(data.get("sessionId") or ""),
            robot_id=str(data.get("robotId") or ""),
            device_id=str(data.get("deviceId") or ""),
            room_name=str(data.get("roomName") or ""),
            livekit_url=str(data.get("livekitUrl") or ""),
            robot_token=str(data.get("robotToken") or ""),
            publish_audio=bool(data.get("publishAudio")),
            subscribe_operator_audio=bool(data.get("subscribeOperatorAudio")),
            publish_video=bool(data.get("publishVideo")),
            expires_at=parse_time(data.get("expiresAt")),
        )


@dataclass
class ControlTarget:
    """普通设备控制指令中的目标设备描述。"""

    scope: str = ""
    device_id: str = ""
    device_type: str = ""
    vendor: str = ""
    model: str = ""

    @classmethod
    def from_json(cls, data: dict[str, Any]) -> "ControlTarget":
        """从 JSON target 字段解析控制目标。"""
        return cls(
            scope=str(data.get("scope") or ""),
            device_id=str(data.get("deviceId") or ""),
            device_type=str(data.get("deviceType") or ""),
            vendor=str(data.get("vendor") or ""),
            model=str(data.get("model") or ""),
        )


@dataclass
class ControlCommand:
    """后端下发给机器人侧的通用设备控制指令。"""

    protocol: str = ""
    version: str = ""
    message_type: str = ""
    command_id: str = ""
    trace_id: str = ""
    robot_id: str = ""
    control_session_id: str = ""
    control_mode: str = ""
    target: ControlTarget = field(default_factory=ControlTarget)
    action: str = ""
    params: dict[str, Any] = field(default_factory=dict)
    policy: dict[str, Any] = field(default_factory=dict)
    seq: int = 0
    issued_at: str = ""

    @classmethod
    def from_json(cls, data: dict[str, Any]) -> "ControlCommand":
        """从后端 JSON payload 构造通用控制指令对象。"""
        return cls(
            protocol=str(data.get("protocol") or ""),
            version=str(data.get("version") or ""),
            message_type=str(data.get("messageType") or ""),
            command_id=str(data.get("commandId") or ""),
            trace_id=str(data.get("traceId") or ""),
            robot_id=str(data.get("robotId") or ""),
            control_session_id=str(data.get("controlSessionId") or ""),
            control_mode=str(data.get("controlMode") or ""),
            target=ControlTarget.from_json(data.get("target") or {}),
            action=str(data.get("action") or ""),
            params=dict(data.get("params") or {}),
            policy=dict(data.get("policy") or {}),
            seq=int(data.get("seq") or 0),
            issued_at=str(data.get("issuedAt") or ""),
        )
