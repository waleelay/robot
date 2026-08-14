import time
import unittest
from types import SimpleNamespace

from robot_media_client.model import StartCommand
from robot_media_client.publisher import ProcessPublisher


class ProcessPublisherTest(unittest.TestCase):
    def publisher(self, retry_seconds: float = 60) -> ProcessPublisher:
        return ProcessPublisher(
            SimpleNamespace(
                publisher_mode="auto",
                ffmpeg_publisher_cmd="ffmpeg-publisher",
                publisher_gstreamer_retry_seconds=retry_seconds,
                publisher_ffmpeg_first_device_ids=set(),
            )
        )

    def test_uses_ffmpeg_during_gstreamer_retry_cooldown(self) -> None:
        publisher = self.publisher()
        rtsp_url = "rtsp://camera/live"
        publisher.gstreamer_failed_rtsp_urls[rtsp_url] = time.monotonic()

        self.assertTrue(publisher._should_start_with_ffmpeg(StartCommand(), rtsp_url))

    def test_retries_gstreamer_after_cooldown(self) -> None:
        publisher = self.publisher()
        rtsp_url = "rtsp://camera/live"
        publisher.gstreamer_failed_rtsp_urls[rtsp_url] = time.monotonic() - 120

        self.assertFalse(publisher._should_start_with_ffmpeg(StartCommand(session_id="session-1"), rtsp_url))
        self.assertNotIn(rtsp_url, publisher.gstreamer_failed_rtsp_urls)

    def test_keeps_explicit_ffmpeg_first_device(self) -> None:
        publisher = self.publisher()
        publisher.cfg.publisher_ffmpeg_first_device_ids = {"camera-1"}

        self.assertTrue(
            publisher._should_start_with_ffmpeg(
                StartCommand(device_id="camera-1"),
                "rtsp://camera/live",
            )
        )


if __name__ == "__main__":
    unittest.main()
