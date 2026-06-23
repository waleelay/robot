from __future__ import annotations

import subprocess


class Probe:
    """使用 ffprobe 校验 RTSP 地址是否能读到视频流。"""

    def __init__(self, path: str, timeout: float) -> None:
        """保存 ffprobe 路径和探测超时时间。"""
        self.path = path
        self.timeout = timeout

    def check(self, url: str) -> None:
        """探测 RTSP 视频流；失败时让 subprocess 抛出异常给调用方。"""
        subprocess.run(
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
        )
