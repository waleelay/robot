import math
import struct
import unittest
from types import SimpleNamespace

from robot_media_client.intercom import IntercomManager
from robot_media_client.model import IntercomStartCommand
from robot_media_client.opus_codec import OpusCodecError, OpusDecoder, OpusEncoder


class MultiFunctionIntercomTest(unittest.TestCase):
    def test_selects_multi_function_bridge_by_registered_device_id(self) -> None:
        cfg = SimpleNamespace(
            multi_function=SimpleNamespace(
                enabled=True,
                device_id="broadcaster-001",
            ),
        )
        manager = IntercomManager(cfg, SimpleNamespace())
        self.assertTrue(manager._is_multi_function_target(IntercomStartCommand(
            device_id="broadcaster-001",
        )))
        self.assertFalse(manager._is_multi_function_target(IntercomStartCommand(
            device_id="speaker_main",
        )))

    def test_opus_round_trip_for_device_frame_sizes(self) -> None:
        try:
            encoder = OpusEncoder(8000)
            decoder = OpusDecoder(8000)
        except OpusCodecError as exc:
            self.skipTest(str(exc))
        self.addCleanup(encoder.close)
        self.addCleanup(decoder.close)
        samples = [
            round(math.sin(2 * math.pi * 440 * index / 8000) * 12000)
            for index in range(480)
        ]
        pcm = struct.pack("<480h", *samples)
        packet = encoder.encode(pcm, 480)
        decoded, decoded_samples = decoder.decode(packet, 960)
        self.assertGreater(len(packet), 0)
        self.assertEqual(decoded_samples, 480)
        self.assertEqual(len(decoded), 960)


if __name__ == "__main__":
    unittest.main()
