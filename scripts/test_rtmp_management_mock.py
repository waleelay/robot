import importlib.util
import json
import pathlib
import sys
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.request import Request, urlopen


MODULE_PATH = pathlib.Path(__file__).with_name("rtmp_management_mock.py")
SPEC = importlib.util.spec_from_file_location("rtmp_management_mock", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class RtmpManagementMockTests(unittest.TestCase):

    def test_http_server_proxies_management_and_bridges_publish_config(self):
        class UpstreamHandler(BaseHTTPRequestHandler):
            def do_GET(self):
                if self.path.startswith("/api/v1/management/fixed-cameras"):
                    self._json({"code": "0", "message": "success", "data": {
                        "records": [], "total": 0, "pageNum": 1, "pageSize": 10,
                    }})
                    return
                self._json({"code": "0", "message": "proxied", "data": {"path": self.path}})

            def _json(self, payload):
                body = json.dumps(payload).encode()
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)

            def log_message(self, *_args):
                return

        class MediaHandler(BaseHTTPRequestHandler):
            def do_GET(self):
                self._json(409, {"code": "FIXED_CAMERA_INGRESS_NOT_CONFIGURED"})

            def do_POST(self):
                self._json(200, {
                    "cameraId": "camera-1",
                    "configured": True,
                    "streamStatus": "OFFLINE",
                    "observedAt": "2026-09-18T10:00:00+08:00",
                    "credentialIssued": True,
                    "url": "rtmp://media/live",
                    "streamKey": "secret-key",
                    "publisherRevision": 0,
                    "ingressOperationRevision": int(self.headers["X-Ingress-Operation-Revision"]),
                })

            def _json(self, status, payload):
                body = json.dumps(payload).encode()
                self.send_response(status)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)

            def log_message(self, *_args):
                return

        upstream = ThreadingHTTPServer(("127.0.0.1", 0), UpstreamHandler)
        media = ThreadingHTTPServer(("127.0.0.1", 0), MediaHandler)
        settings = MODULE.Settings(
            listen_host="127.0.0.1",
            listen_port=0,
            upstream_base_url=f"http://127.0.0.1:{upstream.server_port}",
            media_base_url=f"http://127.0.0.1:{media.server_port}",
            insecure_tls=False,
            camera_id="camera-1",
            camera_name="RTMP测试摄像头",
            map_id="1",
            region_id=None,
            coordinate_x=1.0,
            coordinate_y=2.0,
            heading_yaw=3.0,
        )
        mock_server = MODULE.ManagementMockServer(settings)
        servers = [upstream, media, mock_server]
        threads = [threading.Thread(target=server.serve_forever, daemon=True) for server in servers]
        for thread in threads:
            thread.start()
        try:
            base_url = f"http://127.0.0.1:{mock_server.server_port}"
            with urlopen(f"{base_url}/api/v1/management/fixed-cameras?pageNum=1&pageSize=10") as response:
                fixed_cameras = json.load(response)
            self.assertEqual("camera-1", fixed_cameras["data"]["records"][0]["cameraId"])

            request = Request(
                f"{base_url}/api/v1/management/fixed-cameras/camera-1/publish-config",
                data=b"{}",
                method="POST",
            )
            with urlopen(request) as response:
                publish_config = json.load(response)
            self.assertEqual("rtmp://media/live/secret-key", publish_config["data"]["pushUrl"])

            with urlopen(f"{base_url}/api/v1/management/maps") as response:
                proxied = json.load(response)
            self.assertEqual("proxied", proxied["message"])
        finally:
            for server in servers:
                server.shutdown()
                server.server_close()

    def test_injects_rtmp_camera_into_first_page(self):
        payload = {
            "code": "0",
            "data": {"records": [{"cameraId": "old"}], "total": 1, "pageNum": 1, "pageSize": 10},
        }
        camera = {"cameraId": "new", "mapId": "1"}

        result = MODULE.inject_camera(payload, camera, {"pageNum": ["1"], "mapId": ["1"]})

        self.assertEqual(["old", "new"], [item["cameraId"] for item in result["data"]["records"]])
        self.assertEqual(2, result["data"]["total"])

    def test_does_not_inject_camera_for_another_map(self):
        payload = {"code": "0", "data": {"records": [], "total": 0}}

        result = MODULE.inject_camera(payload, {"cameraId": "new", "mapId": "1"}, {"mapId": ["2"]})

        self.assertEqual([], result["data"]["records"])
        self.assertEqual(0, result["data"]["total"])

    def test_builds_one_time_push_url_and_preserves_observed_at(self):
        result = MODULE.management_publish_config({
            "configured": True,
            "streamStatus": "OFFLINE",
            "observedAt": "2026-09-18T10:00:00+08:00",
            "credentialIssued": True,
            "url": "rtmp://media/live/",
            "streamKey": "/secret",
            "publisherRevision": 0,
            "ingressOperationRevision": 3,
        }, "camera-1")

        self.assertEqual("RTMP", result["protocolType"])
        self.assertEqual("rtmp://media/live/secret", result["pushUrl"])
        self.assertEqual("2026-09-18T10:00:00+08:00", result["observedAt"])
        self.assertEqual(3, result["ingressOperationRevision"])

    def test_never_returns_credentials_for_a_status_response(self):
        result = MODULE.management_publish_config({
            "configured": True,
            "credentialIssued": False,
            "url": "rtmp://media/live",
            "streamKey": "must-not-leak",
        }, "camera-1")

        self.assertIsNone(result["pushUrl"])


if __name__ == "__main__":
    unittest.main()
