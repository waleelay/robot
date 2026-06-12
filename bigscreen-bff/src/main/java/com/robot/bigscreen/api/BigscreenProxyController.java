package com.robot.bigscreen.api;

import com.robot.bigscreen.client.CenterProxyClient;
import jakarta.servlet.http.HttpServletRequest;
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
