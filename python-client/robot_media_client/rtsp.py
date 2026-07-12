from __future__ import annotations

import json
import subprocess
from dataclasses import dataclass


@dataclass
class StreamInfo:
    """RTSP 视频流的基础探测信息。"""

    codec_name: str = ""
    width: int = 0
    height: int = 0


class Probe:
    """使用 ffprobe 校验 RTSP 地址是否能读到视频流。"""

    def __init__(self, path: str, timeout: float) -> None:
        """保存 ffprobe 路径和探测超时时间。"""
        self.path = path
        self.timeout = timeout

    def check(self, url: str) -> StreamInfo:
        """探测 RTSP 视频流；失败时抛出异常，成功时返回首路视频信息。"""
        completed = subprocess.run(
            [
                self.path,
                "-v",
                "error",
                "-rtsp_transport",
                "tcp",
                "-select_streams",
                "v:0",
                "-show_entries",
                "stream=codec_name,width,height",
                "-of",
                "json",
                url,
            ],
            timeout=self.timeout,
            check=True,
            capture_output=True,
            text=True,
        )
        if completed.stdout:
            print(completed.stdout, flush=True)
        data = json.loads(completed.stdout or "{}")
        streams = data.get("streams") or []
        if not streams:
            return StreamInfo()
        stream = streams[0]
        return StreamInfo(
            codec_name=str(stream.get("codec_name") or ""),
            width=int(stream.get("width") or 0),
            height=int(stream.get("height") or 0),
        )
