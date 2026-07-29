import socket
import unittest
from types import SimpleNamespace

from robot_media_client.multifunction import (
    MultiFunctionClient,
    StreamParser,
    build_light_frame,
    parse_device_status,
    percent_to_range,
    volume_command,
)


class MultiFunctionProtocolTest(unittest.TestCase):
    def test_light_frames_match_captured_packets(self) -> None:
        self.assertEqual(build_light_frame(0x04), bytes.fromhex("8D 00 04 61"))
        self.assertEqual(build_light_frame(0x01, b"\x01"), bytes.fromhex("8D 01 01 01 31"))
        self.assertEqual(build_light_frame(0x01, b"\x00"), bytes.fromhex("8D 01 01 00 6F"))
        self.assertEqual(build_light_frame(0x03, b"\x01"), bytes.fromhex("8D 01 03 01 A0"))
        self.assertEqual(build_light_frame(0x07, b"\x02"), bytes.fromhex("8D 01 07 02 79"))
        self.assertEqual(build_light_frame(0x09, b"\xFF\xA2"), bytes.fromhex("8D 02 09 FF A2 1B"))

    def test_volume_and_tilt_mappings_match_capture(self) -> None:
        self.assertEqual(volume_command(47), b"[14]2f")
        self.assertEqual(percent_to_range(0, 80, 220), 80)
        self.assertEqual(percent_to_range(100, 80, 220), 220)
        self.assertEqual(percent_to_range(62, 100, 200), 162)

    def test_parser_handles_fragmented_status_and_quoted_volume(self) -> None:
        parser = StreamParser()
        self.assertEqual(parser.feed(b'[99]"{"volume_real": 3'), [])
        events = parser.feed(
            b'0, "volume_limit": 100, "temperature": "48.49"}"[14]"1e"[30]'
        )
        self.assertEqual([event[0] for event in events], ["status", "volume", "tts_finished"])
        self.assertEqual(events[1][1], b"1e")
        self.assertEqual(
            parse_device_status(events[0][1]),
            {
                "connected": True,
                "volumePercent": 30,
                "volumeLimitPercent": 100,
                "temperatureC": 48.49,
            },
        )

    def test_parser_accepts_single_digit_volume_reply_and_continues(self) -> None:
        parser = StreamParser()
        events = parser.feed(b'[14]"a"[99]"{"volume_real": 10}"')
        self.assertEqual([event[0] for event in events], ["volume", "status"])
        self.assertEqual(events[0][1], b"a")
        self.assertEqual(parse_device_status(events[1][1])["volumePercent"], 10)

    def test_parser_does_not_split_monitor_opus_on_raw_light_header_byte(self) -> None:
        parser = StreamParser()
        opus = b"\x48\x8D\x01\x02\x03\x7f"
        events = parser.feed(b"[40]" + opus + b"[39]")
        self.assertEqual(events, [("monitor_opus", opus), ("audio_finished", b"")])

    def test_parser_splits_monitor_opus_before_valid_light_frame(self) -> None:
        parser = StreamParser()
        light = build_light_frame(0x04)
        events = parser.feed(b"[40]\x48\x01" + light + b"[30]")
        self.assertEqual(events[0], ("monitor_opus", b"\x48\x01"))
        self.assertEqual(events[1], ("light_frame", light))
        self.assertEqual(events[2], ("tts_finished", b""))

    def test_client_writes_real_protocol_to_connected_socket(self) -> None:
        client_socket, device_socket = socket.socketpair()
        self.addCleanup(client_socket.close)
        self.addCleanup(device_socket.close)
        client = MultiFunctionClient(SimpleNamespace(
            enabled=True,
            device_id="broadcaster-001",
            host="127.0.0.1",
            control_port=8519,
            tilt_port=12345,
            http_port=8222,
            dial_timeout=1,
            write_timeout=1,
            http_timeout=1,
            keepalive_enabled=False,
            keepalive_interval=2,
        ))
        client.connection = client_socket
        client.connected.set()
        client.execute("set_volume", {"volumePercent": 47})
        client.execute("light.set", {
            "enabled": True,
            "brightness": 47,
            "strobeEnabled": True,
            "redBlueMode": 2,
        })
        want = b"".join([
            b"[14]2f",
            bytes.fromhex("8D 01 01 01 31"),
            bytes.fromhex("8D 01 02 0E 25"),
            bytes.fromhex("8D 01 03 01 A0"),
            bytes.fromhex("8D 01 07 02 79"),
        ])
        self.assertEqual(device_socket.recv(len(want)), want)


if __name__ == "__main__":
    unittest.main()
