import json
import threading
import unittest
from types import SimpleNamespace
from unittest.mock import patch

from robot_media_client.mqtt_client import RobotMQTTClient


class PublishInfo:
    def wait_for_publish(self, timeout: float) -> None:
        self.timeout = timeout

    def is_published(self) -> bool:
        return True


class MQTTClient:
    def __init__(self) -> None:
        self.messages: list[tuple[str, dict[str, object], int, bool]] = []

    def publish(self, topic: str, body: bytes, qos: int, retain: bool) -> PublishInfo:
        self.messages.append((topic, json.loads(body.decode()), qos, retain))
        return PublishInfo()


class Intercom:
    def __init__(self) -> None:
        self.stopped: list[str] = []

    def stop(self, session_id: str) -> None:
        self.stopped.append(session_id)


class IntercomCallTest(unittest.TestCase):
    def setUp(self) -> None:
        self.mqtt = MQTTClient()
        self.intercom = Intercom()
        with patch("robot_media_client.mqtt_client.make_mqtt_client", return_value=self.mqtt):
            self.client = RobotMQTTClient(
                SimpleNamespace(robot_id="robot-001", client_id="client-1"),
                SimpleNamespace(),
                SimpleNamespace(),
                self.intercom,
            )
        self.client.connected = threading.Event()
        self.client.connected.set()

    def test_invite_cancel_state_and_robot_hangup(self) -> None:
        states = []
        self.client.set_intercom_call_state_handler(states.append)

        call_id = self.client.invite_intercom_call("camera01", "需要人工协助", 30)
        invite_topic, invite, qos, retain = self.mqtt.messages[-1]
        self.assertEqual(invite_topic, "robot/robot-001/media/video/intercom/call/invite")
        self.assertEqual(invite["callId"], call_id)
        self.assertEqual(invite["deviceId"], "camera01")
        self.assertEqual((qos, retain), (1, False))

        self.client._handle_intercom_call_state(
            json.dumps({
                "callId": call_id,
                "robotId": "robot-001",
                "status": "accepted",
                "sessionId": "vs-1",
                "message": "operator accepted",
            }).encode(),
            "robot/robot-001/media/video/intercom/call/state",
        )
        self.assertEqual(states[-1].status, "accepted")

        self.client.end_intercom_call(call_id)
        self.assertEqual(self.intercom.stopped, ["vs-1"])
        stop_topic, stop_state, _, _ = self.mqtt.messages[-1]
        self.assertEqual(stop_topic, "robot/robot-001/media/video/intercom/status")
        self.assertEqual(stop_state["status"], "stopped")

        self.client.cancel_intercom_call(call_id, "用户取消")
        cancel_topic, cancel, _, _ = self.mqtt.messages[-1]
        self.assertEqual(cancel_topic, "robot/robot-001/media/video/intercom/call/cancel")
        self.assertEqual(cancel["reason"], "用户取消")

    def test_invite_requires_connection_and_device(self) -> None:
        with self.assertRaises(ValueError):
            self.client.invite_intercom_call("")
        self.client.connected.clear()
        with self.assertRaises(RuntimeError):
            self.client.invite_intercom_call("camera01")


if __name__ == "__main__":
    unittest.main()
