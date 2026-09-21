#!/usr/bin/env python3
"""RTMP 固定摄像头联调专用 Management 代理。

除固定摄像头测试资源外，请求均透传到已部署的旧 Management。该脚本只用于
部署联调，不得作为正式 Management 或生产容错路径。
"""

from __future__ import annotations

import json
import os
import ssl
import threading
from dataclasses import dataclass
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import parse_qs, urlsplit
from urllib.request import Request, urlopen


FIXED_CAMERA_PATH = "/api/v1/management/fixed-cameras"
IGNORED_REQUEST_HEADERS = {"connection", "content-length", "host", "transfer-encoding"}
IGNORED_RESPONSE_HEADERS = {"connection", "content-length", "transfer-encoding"}


def env_bool(name: str, default: bool = False) -> bool:
    value = os.getenv(name)
    return default if value is None else value.strip().lower() in {"1", "true", "yes", "on"}


@dataclass(frozen=True)
class Settings:
    listen_host: str
    listen_port: int
    upstream_base_url: str
    media_base_url: str
    insecure_tls: bool
    camera_id: str
    camera_name: str
    map_id: str
    region_id: str | None
    coordinate_x: float
    coordinate_y: float
    heading_yaw: float

    @classmethod
    def from_env(cls) -> "Settings":
        upstream = os.getenv("MANAGEMENT_MOCK_UPSTREAM_BASE_URL", "").rstrip("/")
        if not upstream:
            raise ValueError("必须设置 MANAGEMENT_MOCK_UPSTREAM_BASE_URL")
        return cls(
            listen_host=os.getenv("MANAGEMENT_MOCK_LISTEN_HOST", "127.0.0.1"),
            listen_port=int(os.getenv("MANAGEMENT_MOCK_LISTEN_PORT", "18866")),
            upstream_base_url=upstream,
            media_base_url=os.getenv("MANAGEMENT_MOCK_MEDIA_BASE_URL", "http://127.0.0.1:8088").rstrip("/"),
            insecure_tls=env_bool("MANAGEMENT_MOCK_INSECURE_TLS"),
            camera_id=os.getenv("MANAGEMENT_MOCK_CAMERA_ID", "900000000000000001"),
            camera_name=os.getenv("MANAGEMENT_MOCK_CAMERA_NAME", "RTMP联调摄像头"),
            map_id=os.getenv("MANAGEMENT_MOCK_CAMERA_MAP_ID", "1"),
            region_id=os.getenv("MANAGEMENT_MOCK_CAMERA_REGION_ID") or None,
            coordinate_x=float(os.getenv("MANAGEMENT_MOCK_CAMERA_X", "0")),
            coordinate_y=float(os.getenv("MANAGEMENT_MOCK_CAMERA_Y", "0")),
            heading_yaw=float(os.getenv("MANAGEMENT_MOCK_CAMERA_YAW", "0")),
        )

    def camera(self) -> dict[str, Any]:
        return {
            "id": self.camera_id,
            "cameraId": self.camera_id,
            "cameraName": self.camera_name,
            "regionId": self.region_id,
            "mapId": self.map_id,
            "locationDescription": "RTMP 固定摄像头联调资源",
            "coordinateX": self.coordinate_x,
            "coordinateY": self.coordinate_y,
            "headingYaw": self.heading_yaw,
            "protocolType": "RTMP",
            "mainStreamUrl": None,
            "subStreamUrl": None,
            "username": None,
            "password": None,
            "enabled": True,
            "remark": "仅用于 RTMP 全链路联调",
            "mediaTransitionState": "STABLE",
            "publisherRevision": 0,
            "ingressOperationRevision": 0,
        }


def json_bytes(value: Any) -> bytes:
    return json.dumps(value, ensure_ascii=False, separators=(",", ":")).encode("utf-8")


def inject_camera(payload: dict[str, Any], camera: dict[str, Any], query: dict[str, list[str]]) -> dict[str, Any]:
    data = payload.get("data")
    if not isinstance(data, dict):
        raise ValueError("Management 固定摄像头列表响应缺少 data")
    records = data.get("records")
    if not isinstance(records, list):
        raise ValueError("Management 固定摄像头列表响应缺少 records")
    requested_map = (query.get("mapId") or [None])[0]
    page_num = int((query.get("pageNum") or ["1"])[0])
    should_include = page_num == 1 and (requested_map is None or str(requested_map) == str(camera["mapId"]))
    existing = any(str(item.get("cameraId", item.get("id"))) == str(camera["cameraId"]) for item in records)
    filtered = [item for item in records if str(item.get("cameraId", item.get("id"))) != str(camera["cameraId"])]
    if should_include:
        filtered.append(camera)
    copied = dict(payload)
    copied_data = dict(data)
    copied_data["records"] = filtered
    old_total = int(data.get("total", len(records)))
    copied_data["total"] = old_total + (1 if should_include and not existing else 0)
    copied["data"] = copied_data
    return copied


def management_publish_config(media: dict[str, Any], camera_id: str) -> dict[str, Any]:
    push_url = None
    if media.get("credentialIssued"):
        base_url = str(media.get("url") or "").rstrip("/")
        stream_key = str(media.get("streamKey") or "").lstrip("/")
        if not base_url or not stream_key:
            raise ValueError("Media 未返回完整的一次性推流凭据")
        push_url = f"{base_url}/{stream_key}"
    return {
        "cameraId": camera_id,
        "protocolType": "RTMP",
        "configured": bool(media.get("configured")),
        "streamStatus": media.get("streamStatus"),
        "reasonCode": media.get("reasonCode"),
        "observedAt": media.get("observedAt"),
        "mediaTransitionState": "STABLE",
        "publisherRevision": int(media.get("publisherRevision") or 0),
        "ingressOperationRevision": int(media.get("ingressOperationRevision") or 0),
        "credentialIssued": bool(media.get("credentialIssued")),
        "pushUrl": push_url,
    }


class ManagementMockHandler(BaseHTTPRequestHandler):
    server: "ManagementMockServer"

    def do_GET(self) -> None:
        self._dispatch()

    def do_POST(self) -> None:
        self._dispatch()

    def do_PUT(self) -> None:
        self._dispatch()

    def do_PATCH(self) -> None:
        self._dispatch()

    def do_DELETE(self) -> None:
        self._dispatch()

    def _dispatch(self) -> None:
        parsed = urlsplit(self.path)
        if parsed.path == "/health":
            self._send_json(HTTPStatus.OK, {"status": "UP", "mode": "rtmp-management-mock"})
            return
        if parsed.path == FIXED_CAMERA_PATH and self.command == "GET":
            self._fixed_camera_list(parsed.query)
            return
        camera_base = f"{FIXED_CAMERA_PATH}/{self.server.settings.camera_id}"
        if parsed.path == camera_base and self.command == "GET":
            self._send_management(self.server.settings.camera())
            return
        if parsed.path == f"{camera_base}/publish-config":
            self._publish_config()
            return
        self._proxy(self.server.settings.upstream_base_url)

    def _fixed_camera_list(self, query_string: str) -> None:
        status, headers, body = self._request(self.server.settings.upstream_base_url)
        if status >= 400:
            self._send(status, headers, body)
            return
        try:
            payload = json.loads(body.decode("utf-8"))
            result = inject_camera(payload, self.server.settings.camera(), parse_qs(query_string))
        except (ValueError, TypeError, json.JSONDecodeError) as exception:
            self._send_json(HTTPStatus.BAD_GATEWAY, {
                "code": "MANAGEMENT_MOCK_UPSTREAM_INVALID",
                "message": str(exception),
                "data": None,
            })
            return
        self._send_json(HTTPStatus.OK, result)

    def _publish_config(self) -> None:
        if self.command not in {"GET", "POST", "PUT", "DELETE"}:
            self._send_json(HTTPStatus.METHOD_NOT_ALLOWED, {"code": "METHOD_NOT_ALLOWED", "message": "方法不支持"})
            return
        camera_id = self.server.settings.camera_id
        media_path = f"/internal/media/fixed-camera-ingresses/{camera_id}"
        if self.command == "POST":
            media_path = "/internal/media/fixed-camera-ingresses"
        with self.server.revision_lock:
            revision = None
            if self.command in {"POST", "PUT", "DELETE"}:
                revision = self._next_ingress_revision(camera_id)
            body = json_bytes({"cameraId": camera_id}) if self.command == "POST" else b""
            extra_headers = {"Content-Type": "application/json"}
            if revision is not None:
                extra_headers["X-Ingress-Operation-Revision"] = str(revision)
            status, _headers, response_body = self._request(
                self.server.settings.media_base_url,
                path=media_path,
                method=self.command,
                body=body,
                extra_headers=extra_headers,
            )
        if self.command == "DELETE" and status < 400:
            self._send_management(None)
            return
        try:
            media = json.loads(response_body.decode("utf-8")) if response_body else {}
        except json.JSONDecodeError:
            self._send_json(HTTPStatus.BAD_GATEWAY, {
                "code": "MANAGEMENT_MOCK_MEDIA_INVALID",
                "message": "Media 返回了非 JSON 响应",
                "data": None,
            })
            return
        if status >= 400:
            self._send_json(status, media)
            return
        self._send_management(management_publish_config(media, camera_id))

    def _next_ingress_revision(self, camera_id: str) -> int:
        status, _headers, body = self._request(
            self.server.settings.media_base_url,
            path=f"/internal/media/fixed-camera-ingresses/{camera_id}",
            method="GET",
            body=b"",
        )
        if status >= 400 or not body:
            return 1
        try:
            current = json.loads(body.decode("utf-8"))
            return int(current.get("ingressOperationRevision") or 0) + 1
        except (ValueError, TypeError, json.JSONDecodeError):
            return 1

    def _proxy(self, base_url: str) -> None:
        status, headers, body = self._request(base_url)
        self._send(status, headers, body)

    def _request(
            self,
            base_url: str,
            *,
            path: str | None = None,
            method: str | None = None,
            body: bytes | None = None,
            extra_headers: dict[str, str] | None = None) -> tuple[int, dict[str, str], bytes]:
        request_body = body if body is not None else self._read_body()
        target = base_url + (path if path is not None else self.path)
        headers = {
            name: value for name, value in self.headers.items()
            if name.lower() not in IGNORED_REQUEST_HEADERS
        }
        headers.update(extra_headers or {})
        request = Request(target, data=request_body or None, headers=headers, method=method or self.command)
        context = None
        if self.server.settings.insecure_tls and target.lower().startswith("https://"):
            context = ssl._create_unverified_context()
        try:
            with urlopen(request, timeout=10, context=context) as response:
                return response.status, dict(response.headers.items()), response.read()
        except HTTPError as exception:
            return exception.code, dict(exception.headers.items()), exception.read()
        except (URLError, TimeoutError) as exception:
            return HTTPStatus.BAD_GATEWAY, {"Content-Type": "application/json"}, json_bytes({
                "code": "MANAGEMENT_MOCK_UPSTREAM_UNAVAILABLE",
                "message": f"下游服务不可用：{type(exception).__name__}",
                "data": None,
            })

    def _read_body(self) -> bytes:
        length = int(self.headers.get("Content-Length") or 0)
        return self.rfile.read(length) if length > 0 else b""

    def _send_management(self, data: Any) -> None:
        self._send_json(HTTPStatus.OK, {"code": "0", "message": "success", "data": data, "traceId": ""})

    def _send_json(self, status: int, payload: Any) -> None:
        self._send(status, {"Content-Type": "application/json; charset=utf-8"}, json_bytes(payload))

    def _send(self, status: int, headers: dict[str, str], body: bytes) -> None:
        self.send_response(int(status))
        for name, value in headers.items():
            if name.lower() not in IGNORED_RESPONSE_HEADERS:
                self.send_header(name, value)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format_string: str, *args: Any) -> None:
        print(f"Management Mock {self.address_string()} {format_string % args}", flush=True)


class ManagementMockServer(ThreadingHTTPServer):
    daemon_threads = True

    def __init__(self, settings: Settings):
        super().__init__((settings.listen_host, settings.listen_port), ManagementMockHandler)
        self.settings = settings
        self.revision_lock = threading.Lock()


def main() -> None:
    settings = Settings.from_env()
    server = ManagementMockServer(settings)
    print(
        f"RTMP Management Mock 已启动：http://{settings.listen_host}:{settings.listen_port}，"
        f"cameraId={settings.camera_id}，mapId={settings.map_id}",
        flush=True,
    )
    server.serve_forever()


if __name__ == "__main__":
    main()
