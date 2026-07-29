import unittest
from types import SimpleNamespace
from unittest.mock import patch

from robot_media_client.model import ControlCommand, ControlTarget
from robot_media_client.mqtt_client import RobotMQTTClient


class MultiFunctionControlTest(unittest.TestCase):
    def setUp(self) -> None:
        self.adapter = FakeMultiFunctionAdapter()
        with patch("robot_media_client.mqtt_client.make_mqtt_client", return_value=SimpleNamespace()):
            self.client = RobotMQTTClient(
                SimpleNamespace(
                    robot_id="robot-001",
                    client_id="client-1",
                    multi_function=SimpleNamespace(device_id="broadcaster-001"),
                ),
                SimpleNamespace(),
                SimpleNamespace(),
                SimpleNamespace(),
                self.adapter,
            )
        self.target = ControlTarget(
            device_id="broadcaster-001",
            device_type="MULTI_FUNCTION_BROADCASTER",
        )

    def command(self, action: str, params: dict[str, object]) -> ControlCommand:
        return ControlCommand(target=self.target, action=action, params=params)

    def test_uses_real_adapter_state(self) -> None:
        self.assertTrue(self.client.apply_control_command(
            self.command("set_volume", {"volumePercent": 37})
        ))
        status = self.client.device_state["broadcaster-001"]
        self.assertEqual(self.adapter.action, "set_volume")
        self.assertEqual(self.adapter.params, {"volumePercent": 37})
        self.assertTrue(status["connected"])
        self.assertEqual(status["volumePercent"], 37)


class FakeMultiFunctionAdapter:
    def __init__(self) -> None:
        self.action = ""
        self.params: dict[str, object] = {}
        self.handler = None
        self.state: dict[str, object] = {"connected": True, "volumePercent": 37}

    def snapshot(self) -> dict[str, object]:
        return dict(self.state)

    def set_state_handler(self, handler) -> None:
        self.handler = handler

    def execute(self, action: str, params: dict[str, object]) -> dict[str, object]:
        self.action = action
        self.params = params
        return dict(self.state)


if __name__ == "__main__":
    unittest.main()
