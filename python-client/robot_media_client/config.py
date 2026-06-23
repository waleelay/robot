from __future__ import annotations

import os
from dataclasses import dataclass


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
    ffprobe_path: str
    publisher_cmd: str
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
    robot_id = env("ROBOT_ID", "robot-001")
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
        ffprobe_path=env("FFPROBE_PATH", "ffprobe"),
        publisher_cmd=env("PUBLISHER_CMD", ""),
        ffmpeg_publisher_cmd=env("FFMPEG_PUBLISHER_CMD", "./scripts/ffmpeg-livekit-publisher.sh {rtsp} {livekitUrl} {token}"),
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
    if robot_id == "robot-002":
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


def default_robot_name(robot_id: str) -> str:
    """返回已知 robotId 的默认展示名称。"""
    if robot_id == "robot-002":
        return "云深处四足机器狗"
    return "松灵四轮机器人"


def default_type(robot_id: str) -> str:
    """返回已知 robotId 的默认机器人类型。"""
    if robot_id == "robot-002":
        return "四足机器人"
    return "轮式机器人"


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


def bounded_percentage(value: int) -> int:
    """把百分比限制在 0 到 100 之间。"""
    return max(0, min(100, value))
