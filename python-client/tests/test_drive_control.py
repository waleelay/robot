import io
import unittest
from contextlib import redirect_stdout
from types import SimpleNamespace
from unittest.mock import patch

from robot_media_client.model import ControlCommand, ControlTarget
from robot_media_client.mqtt_client import RobotMQTTClient


class DriveControlTest(unittest.TestCase):
    def setUp(self) -> None:
        with patch("robot_media_client.mqtt_client.make_mqtt_client", return_value=SimpleNamespace()):
            self.client = RobotMQTTClient(
                SimpleNamespace(robot_id="robot-001", client_id="client-1", multi_function=None),
                SimpleNamespace(),
                SimpleNamespace(),
                SimpleNamespace(),
            )

    @staticmethod
    def command(params: dict[str, object]) -> ControlCommand:
        return ControlCommand(
            robot_id="robot-001",
            seq=1024,
            target=ControlTarget(device_id="base", device_type="WHEELED_BASE"),
            action="drive.velocity",
            params=params,
        )

    def test_simulates_nested_drive_velocity_without_driver_call(self) -> None:
        output = io.StringIO()
        with redirect_stdout(output):
            applied = self.client.apply_control_command(self.command({
                "linear": {"x": 0.3, "y": 0.2, "z": 0.0},
                "angular": {"roll": 0.0, "yaw": -0.2},
            }))

        self.assertTrue(applied)
        self.assertIn("robot body command simulated", output.getvalue())
        self.assertIn("angular.yaw=-0.2", output.getvalue())

    def test_rejects_incomplete_motion_vectors(self) -> None:
        output = io.StringIO()
        with redirect_stdout(output):
            applied = self.client.apply_control_command(self.command({
                "linear": {"x": 0.3, "y": 0.0, "z": 0.0},
                "angular": {"yaw": -0.2},
            }))

        self.assertFalse(applied)
        self.assertIn("invalid-motion-values", output.getvalue())


if __name__ == "__main__":
    unittest.main()
