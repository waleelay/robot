"""机器人媒体客户端入口模块。"""

from __future__ import annotations

import signal
import threading
import time

from . import config
from .intercom import IntercomManager
from .mqtt_client import RobotMQTTClient
from .publisher import ProcessPublisher
from .recordingupload import Runner
from .rtsp import Probe


def main() -> None:
    """客户端入口：加载配置，启动 MQTT、视频发布、对讲和录像上传后台任务。"""
    cfg = config.load()
    stop_event = threading.Event()

    def request_stop(_signum: int, _frame: object) -> None:
        """接收 SIGINT/SIGTERM 后请求主循环退出。"""
        stop_event.set()

    signal.signal(signal.SIGINT, request_stop)
    signal.signal(signal.SIGTERM, request_stop)

    # 这些对象在 MQTT 重连之间复用，避免断线重连时丢失本地推流和对讲管理上下文。
    probe = Probe(cfg.ffprobe_path, cfg.probe_timeout)
    publisher = ProcessPublisher(cfg)
    intercom = IntercomManager(cfg)
    runner: Runner | None = None
    if cfg.recording_upload_enabled:
        runner = Runner(cfg)
        threading.Thread(target=runner.run, daemon=True).start()
    else:
        print("recording upload disabled", flush=True)

    print(
        "client starting",
        f"robotId={cfg.robot_id}",
        f"recordingUploadEnabled={cfg.recording_upload_enabled}",
        f"recordingDirectory={cfg.recording_directory}",
        f"mediaService={cfg.media_service_url}",
        flush=True,
    )
    while not stop_event.is_set():
        # MQTT client 对象按连接生命周期创建；断线后统一清理，再进入退避重连。
        client = RobotMQTTClient(cfg, probe, publisher, intercom)
        mqtt_thread = threading.Thread(target=client.run)
        mqtt_thread.start()
        while mqtt_thread.is_alive() and not stop_event.is_set():
            mqtt_thread.join(timeout=0.2)
        client.stop()
        mqtt_thread.join(timeout=5)
        if not stop_event.is_set():
            print("mqtt client stopped; reconnecting after backoff", flush=True)
            time.sleep(5)

    publisher.stop_all()
    intercom.stop_all()
    if runner is not None:
        runner.stop()
