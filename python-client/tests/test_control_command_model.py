import unittest

from robot_media_client.model import ControlCommand


class ControlCommandModelTest(unittest.TestCase):
    def test_preserves_nested_drive_velocity_vectors(self) -> None:
        command = ControlCommand.from_json({
            "robotId": "robot-001",
            "seq": 1024,
            "target": {
                "deviceId": "base",
                "deviceType": "WHEELED_BASE",
            },
            "action": "drive.velocity",
            "params": {
                "linear": {"x": 0.3, "y": 0.2, "z": 0.0},
                "angular": {"roll": 0.0, "yaw": -0.2},
            },
            "issuedAt": "2026-09-13T10:30:00+08:00",
        })

        self.assertEqual(command.robot_id, "robot-001")
        self.assertEqual(command.seq, 1024)
        self.assertEqual(command.params["linear"]["y"], 0.2)
        self.assertEqual(command.params["angular"]["yaw"], -0.2)


if __name__ == "__main__":
    unittest.main()
