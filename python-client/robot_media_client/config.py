from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path


@dataclass
class Camera:
    """机器人侧单路摄像头配置和 RTSP 地址集合。"""

    camera_id: str
    device_id: str
    name: str
    group_type: str
    channel: str
    quality: str
    rtsp_url: str
    rtsp_main_url: str
    rtsp_sub_url: str


@dataclass
class Device:
    """机器人本体或上装设备能力与运行状态配置。"""

    device_id: str
    binding_id: str
    scope: str
    device_type: str
    display_name: str
    online_status: str
    control_status: str
    enabled: bool
    actions: list[str]
    vendor: str = ""
    model: str = ""
    risk_level: str = ""
    status: dict[str, object] | None = None
    control_profile: dict[str, object] | None = None


@dataclass
class Config:
    """Python 客户端运行配置，主要从环境变量加载。"""

    robot_id: str
    robot_name: str
    type: str
    battery: int
    mqtt_broker: str
    mqtt_username: str
    mqtt_password: str
    client_id: str
    rtsp_visible_sub: str
    rtsp_visible_main: str
    rtsp_thermal_sub: str
    rtsp_thermal_main: str
    cameras: list[Camera]
    devices: list[Device]
    ffprobe_path: str
    publisher_cmd: str
    publisher_mode: str
    publisher_fallback_watch_seconds: float
    publisher_ffmpeg_first_device_ids: set[str]
    ffmpeg_publisher_cmd: str
    gstreamer_publisher_path: str
    gstreamer_pipeline: str
    gst_launch_path: str
    audio_capture_pipeline: str
    audio_playback_pipeline: str
    probe_timeout: float
    heartbeat_interval: float
    recording_upload_enabled: bool
    media_service_url: str
    recording_directory: str
    recording_manifest_path: str
    recording_device_id: str
    upload_scan_interval: float
    upload_part_concurrency: int
    upload_part_url_batch_size: int
    upload_file_concurrency: int
    local_cache_max_bytes: int
    local_min_free_bytes: int
    local_retention_after_ready: float
    intercom_audio_enabled: bool


def load() -> Config:
    """读取环境变量并组装完整客户端配置。"""
    robot_id = env("ROBOT_ID", "test001")
    return Config(
        robot_id=robot_id,
        robot_name=env("ROBOT_NAME", default_robot_name(robot_id)),
        type=env("ROBOT_TYPE", default_type(robot_id)),
        battery=bounded_percentage(env_int("ROBOT_BATTERY", 100)),
        mqtt_broker=env("MQTT_BROKER_URL", "tcp://192.168.124.77:1883"),
        mqtt_username=env("MQTT_USERNAME", ""),
        mqtt_password=env("MQTT_PASSWORD", ""),
        client_id=env("ROBOT_CLIENT_ID", "robot-media-client"),
        rtsp_visible_sub=env("RTSP_VISIBLE_SUB", "rtsp://admin:okwy1688@192.168.1.64:554/Streaming/Channels/102"),
        rtsp_visible_main=env("RTSP_VISIBLE_MAIN", "rtsp://admin:okwy1688@192.168.1.64:554/Streaming/Channels/101"),
        rtsp_thermal_sub=env("RTSP_THERMAL_SUB", "rtsp://admin:okwy1688@192.168.1.65:554/Streaming/Channels/102"),
        rtsp_thermal_main=env("RTSP_THERMAL_MAIN", "rtsp://admin:okwy1688@192.168.1.65:554/Streaming/Channels/102"),
        cameras=cameras(robot_id),
        devices=devices(robot_id),
        ffprobe_path=env("FFPROBE_PATH", "ffprobe"),
        publisher_cmd=env("PUBLISHER_CMD", ""),
        publisher_mode=env("PUBLISHER_MODE", "auto").lower(),
        publisher_fallback_watch_seconds=env_int("PUBLISHER_FALLBACK_WATCH_SECONDS", 8),
        publisher_ffmpeg_first_device_ids=env_csv_set("PUBLISHER_FFMPEG_FIRST_DEVICE_IDS", ""),
        ffmpeg_publisher_cmd=env("FFMPEG_PUBLISHER_CMD", default_ffmpeg_publisher_cmd()),
        gstreamer_publisher_path=env("GSTREAMER_PUBLISHER_PATH", "gstreamer-publisher"),
        gstreamer_pipeline=env("GSTREAMER_PIPELINE", "rtspsrc location={rtsp} protocols=tcp latency=100 ! queue ! rtph264depay ! h264parse config-interval=1"),
        gst_launch_path=env("GST_LAUNCH_PATH", "gst-launch-1.0"),
        audio_capture_pipeline=env("AUDIO_CAPTURE_PIPELINE", "autoaudiosrc ! audioconvert ! audioresample ! audio/x-raw,format=S16LE,rate=48000,channels=1,layout=interleaved ! fdsink fd=1"),
        audio_playback_pipeline=env("AUDIO_PLAYBACK_PIPELINE", "fdsrc fd=0 ! audio/x-raw,format=S16LE,rate=48000,channels=1,layout=interleaved ! audioconvert ! audioresample ! autoaudiosink"),
        probe_timeout=env_int("PROBE_TIMEOUT_MS", 8000) / 1000,
        heartbeat_interval=env_int("HEARTBEAT_INTERVAL_MS", 5000) / 1000,
        recording_upload_enabled=env_bool("RECORDING_UPLOAD_ENABLED", True),
        media_service_url=env("MEDIA_SERVICE_URL", "http://192.168.124.77:8088"),
        recording_directory=env("RECORDING_DIRECTORY", "./recordings"),
        recording_manifest_path=env("RECORDING_MANIFEST_PATH", "./recording-upload-manifest.json"),
        recording_device_id=env("RECORDING_DEVICE_ID", "camera01"),
        upload_scan_interval=env_int("RECORDING_UPLOAD_SCAN_INTERVAL_MS", 30000) / 1000,
        upload_part_concurrency=env_int("RECORDING_UPLOAD_PART_CONCURRENCY", 4),
        upload_part_url_batch_size=env_int("RECORDING_UPLOAD_PART_URL_BATCH_SIZE", 16),
        upload_file_concurrency=env_int("RECORDING_UPLOAD_FILE_CONCURRENCY", 2),
        local_cache_max_bytes=env_int("RECORDING_LOCAL_CACHE_MAX_BYTES", 107374182400),
        local_min_free_bytes=env_int("RECORDING_LOCAL_MIN_FREE_BYTES", 10737418240),
        local_retention_after_ready=env_int("RECORDING_LOCAL_RETENTION_AFTER_READY_HOURS", 24) * 3600,
        intercom_audio_enabled=env_bool("INTERCOM_AUDIO_ENABLED", True),
    )


def cameras(robot_id: str) -> list[Camera]:
    """根据 robotId 生成默认摄像头列表，并允许环境变量覆盖 RTSP 和名称。"""
    ids = ["camera01", "camera02", "camera03"]
    names = ["云台-可见光", "云台-热成像", "本体相机"]
    group_types = ["dual_gimbal", "dual_gimbal", "body"]
    if robot_id == "test002":
        ids = ["camera04", "camera05", "camera06"]
        names = ["头部双光云台", "腹部导航相机", "尾部避障相机"]
        group_types = ["dual_gimbal", "body", "body"]
    result: list[Camera] = []
    for index, camera_id in enumerate(ids):
        upper_id = camera_id.upper()
        env_prefix = "RTSP_" + upper_id
        legacy_rtsp = env(env_prefix, "rtsp://192.168.124.204:8554/" + camera_id)
        sub_rtsp = env(env_prefix + "_SUB", legacy_rtsp)
        main_rtsp = env(env_prefix + "_MAIN", sub_rtsp)
        result.append(Camera(
            camera_id=camera_id,
            device_id=camera_id,
            name=env("CAMERA_" + upper_id + "_NAME", names[index]),
            group_type=env("CAMERA_" + upper_id + "_GROUP_TYPE", group_types[index]),
            channel="visible",
            quality=env("CAMERA_" + upper_id + "_QUALITY", "auto"),
            rtsp_url=legacy_rtsp,
            rtsp_main_url=main_rtsp,
            rtsp_sub_url=sub_rtsp,
        ))
    return result


def devices(robot_id: str) -> list[Device]:
    """生成默认设备能力列表，随 client/status 上报给后端和前端。"""
    base_type = "WHEELED_BASE"
    base_vendor = "SONGLING"
    base_model = "SCOUT"
    max_linear_x = 1.0
    max_linear_y = 0.4
    max_angular_z = 0.8
    if robot_id == "test002":
        base_type = "QUADRUPED_BASE"
        base_vendor = "DEEPNROBOTICS"
        base_model = "X30"
        max_linear_x = 0.8
        max_angular_z = 0.6

    result = [
        Device(
            device_id="base",
            binding_id="bind-base",
            scope="BODY",
            device_type=base_type,
            display_name="机器人本体",
            vendor=base_vendor,
            model=base_model,
            online_status="online",
            control_status="idle",
            enabled=True,
            actions=["drive.velocity", "navigation.return_home", "docking.leave"],
            control_profile={
                "maxLinearX": max_linear_x,
                "maxLinearY": max_linear_y,
                "maxAngularZ": max_angular_z,
                "controlFrameRateHz": 10,
            },
        ),
        Device(
            device_id="ptz-dual-001",
            binding_id="bind-ptz-dual-001",
            scope="PAYLOAD",
            device_type="DUAL_LIGHT_PTZ",
            display_name="双光云台",
            vendor="CUSTOM",
            model="DL-PTZ-01",
            online_status="online",
            control_status="idle",
            enabled=True,
            actions=["ptz.move", "ptz.auto_rotate", "ptz.home", "camera.zoom"],
            status={"autoRotateEnabled": False, "panSpeed": 0},
            control_profile={"maxPanSpeed": 1.0, "maxTiltSpeed": 1.0, "controlFrameRateHz": 10},
        ),
        Device(
            device_id="audio-control-001",
            binding_id="bind-audio-control-001",
            scope="AUDIO",
            device_type="CLIENT_AUDIO",
            display_name="客户端音量",
            online_status="online",
            control_status="idle",
            enabled=True,
            actions=["volume.set", "volume.up", "volume.down", "volume.mute"],
            status={"volume": 50, "muted": False},
            control_profile={"step": 5, "minVolume": 0, "maxVolume": 100},
        ),
    ]
    if robot_id != "robot-unitree-001":
        result.extend([
            Device(
                device_id="launcher-001",
                binding_id="bind-launcher-001",
                scope="PAYLOAD",
                device_type="LAUNCHER",
                display_name="六联发射器",
                vendor="CUSTOM",
                model="LCH-06",
                online_status="online",
                control_status="idle",
                enabled=True,
                risk_level="HIGH",
                actions=["payload.safety_switch", "payload.fire"],
                control_profile={"channels": [1, 2, 3, 4, 5, 6], "requiresConfirm": True, "requiresSafetySwitch": True},
            ),
            Device(
                device_id="net-gun-001",
                binding_id="bind-net-gun-001",
                scope="PAYLOAD",
                device_type="NET_GUN",
                display_name="捕网枪",
                vendor="CUSTOM",
                model="NL-01",
                online_status="online",
                control_status="idle",
                enabled=True,
                risk_level="HIGH",
                actions=["payload.fire"],
                control_profile={"requiresConfirm": True, "cooldownMs": 3000},
            ),
        ])
    result.extend([
        Device(
            device_id="warning-light-left",
            binding_id="bind-warning-light-left",
            scope="PAYLOAD",
            device_type="WARNING_LIGHT",
            display_name="左警示灯",
            vendor="CUSTOM",
            model="WL-01",
            online_status="online",
            control_status="idle",
            enabled=True,
            actions=["light.warning.set"],
            status={"enabled": False},
            control_profile={"modes": ["ON", "OFF"]},
        ),
        Device(
            device_id="warning-light-right",
            binding_id="bind-warning-light-right",
            scope="PAYLOAD",
            device_type="WARNING_LIGHT",
            display_name="右警示灯",
            vendor="CUSTOM",
            model="WL-01",
            online_status="online",
            control_status="idle",
            enabled=True,
            actions=["light.warning.set"],
            status={"enabled": False},
            control_profile={"modes": ["ON", "OFF"]},
        ),
        Device(
            device_id="vehicle-light",
            binding_id="bind-vehicle-light",
            scope="PAYLOAD",
            device_type="VEHICLE_LIGHT",
            display_name="车灯光",
            vendor="CUSTOM",
            model="VL-01",
            online_status="online",
            control_status="idle",
            enabled=True,
            actions=["light.vehicle.set"],
            control_profile={
                "parts": ["front", "rear"],
                "modes": ["OFF", "ON", "BREATH", "CUSTOM"],
                "modeMapping": {"OFF": 0, "ON": 1, "BREATH": 2, "CUSTOM": 3},
                "maxBrightness": 100,
                "rosTopic": "/robot_light_ctl",
                "rosType": "robot_status_core/RobotLightCmd",
            },
        ),
        Device(
            device_id="intercom-001",
            binding_id="bind-intercom-001",
            scope="AUDIO",
            device_type="INTERCOM",
            display_name="语音对讲",
            online_status="online",
            control_status="idle",
            enabled=True,
            actions=["volume.set", "volume.up", "volume.down", "volume.mute"],
            status={"volume": 50, "muted": False},
        ),
    ])
    return result


def default_robot_name(robot_id: str) -> str:
    """返回已知 robotId 的默认展示名称。"""
    if robot_id == "test002":
        return "G1四足机器狗"
    return "R1轮式机器人"


def default_type(robot_id: str) -> str:
    """返回已知 robotId 的默认机器人类型。"""
    if robot_id == "test002":
        return "四足机器狗"
    return "轮式机器人"


def default_ffmpeg_publisher_cmd() -> str:
    """返回可用的 ffmpeg fallback publisher 命令模板。"""
    package_root = Path(__file__).resolve().parents[1]
    candidates = [
        package_root / "scripts" / "ffmpeg-livekit-publisher.sh",
        package_root.parent / "client" / "scripts" / "ffmpeg-livekit-publisher.sh",
        Path.cwd() / "scripts" / "ffmpeg-livekit-publisher.sh",
        Path.cwd().parent / "client" / "scripts" / "ffmpeg-livekit-publisher.sh",
    ]
    for candidate in candidates:
        if candidate.exists():
            return f"{candidate} {{rtsp}} {{livekitUrl}} {{token}}"
    return "ffmpeg-livekit-publisher.sh {rtsp} {livekitUrl} {token}"


def env(key: str, fallback: str) -> str:
    """读取字符串环境变量，空值时使用默认值。"""
    return os.environ.get(key) or fallback


def env_int(key: str, fallback: int) -> int:
    """读取整数环境变量，非法值时使用默认值。"""
    try:
        return int(os.environ.get(key) or fallback)
    except ValueError:
        return fallback


def env_bool(key: str, fallback: bool) -> bool:
    """读取布尔环境变量，支持常见 true/false 写法。"""
    value = os.environ.get(key)
    if value is None or value == "":
        return fallback
    return value.lower() in {"1", "t", "true", "y", "yes", "on"}


def env_csv_set(key: str, fallback: str) -> set[str]:
    """读取逗号分隔环境变量，返回去空白后的集合。"""
    raw = os.environ.get(key)
    if raw is None:
        raw = fallback
    return {part.strip() for part in raw.split(",") if part.strip()}


def bounded_percentage(value: int) -> int:
    """把百分比限制在 0 到 100 之间。"""
    return max(0, min(100, value))
