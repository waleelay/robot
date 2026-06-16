package com.robot.bigscreen.api;

import com.robot.bigscreen.client.CenterProxyClient;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BigscreenProxyController {

    private final CenterProxyClient proxyClient;

    public BigscreenProxyController(CenterProxyClient proxyClient) {
        this.proxyClient = proxyClient;
    }

    @RequestMapping("/api/control/robots")
    public ResponseEntity<Map<String, Object>> removedRobots() {
        return ResponseEntity.status(HttpStatus.GONE).body(Map.of(
                "code", "API_REMOVED",
                "message", "Use /api/bigscreen/panorama/overview instead of /api/control/robots."));
    }

    @RequestMapping(value = "/api/control/video-sessions/{sessionId}/snapshots/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> forwardSnapshotFile(HttpServletRequest request) {
        return proxyClient.forward(request, null);
    }

    @RequestMapping({
            "/api/control/**",
            "/api/bigscreen/**",
            "/api/media/**",
            "/api/manage/**",
            "/internal/media/**"
    })
    public ResponseEntity<byte[]> forward(HttpServletRequest request, @RequestBody(required = false) byte[] body) {
        return proxyClient.forward(request, body);
    }
}
