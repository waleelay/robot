import io
import unittest
from unittest.mock import Mock

from robot_media_client.recordingupload.client import Client


class RecordingUploadClientTest(unittest.TestCase):
    def test_put_part_uses_configured_weak_network_timeout(self):
        client = Client("http://media-service:8088", "robot-001", part_timeout=1800)
        response = Mock(status_code=200)
        client.session.put = Mock(return_value=response)

        client.put_part("http://minio/upload", io.BytesIO(b"part"), 4)

        client.session.put.assert_called_once_with(
            "http://minio/upload",
            data=unittest.mock.ANY,
            headers={"Content-Length": "4"},
            timeout=1800,
        )


if __name__ == "__main__":
    unittest.main()
