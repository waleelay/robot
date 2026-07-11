package com.robot.bigscreen.api;

import com.robot.bigscreen.client.CenterProxyClient;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
public class BusinessTaskProxyController {

    private static final String BUSINESS_PREFIX = "/api/bigscreen/business";

    private final CenterProxyClient proxyClient;

    public BusinessTaskProxyController(CenterProxyClient proxyClient) {
        this.proxyClient = proxyClient;
    }

    @RequestMapping("/api/bigscreen/business/**")
    public ResponseEntity<byte[]> forward(HttpServletRequest request) {
        return proxyClient.forwardToManage(request, targetPath(request.getRequestURI()));
    }

    private String targetPath(String requestPath) {
        String path = requestPath.substring(BUSINESS_PREFIX.length());
        if (path.equals("/tasks/plans") || path.startsWith("/tasks/plans/")) {
            return "/api/v1/management/task-workflow-plans" + path.substring("/tasks/plans".length());
        }
        if (path.equals("/tasks/workflow-definitions") || path.startsWith("/tasks/workflow-definitions/")) {
            return "/api/v1/management/task-workflow-definitions"
                    + path.substring("/tasks/workflow-definitions".length());
        }
        if (path.equals("/tasks/execution-records") || path.startsWith("/tasks/execution-records/")) {
            return "/api/v1/management/task-workflow-instances"
                    + path.substring("/tasks/execution-records".length());
        }
        if (path.equals("/devices") || path.startsWith("/devices/")) {
            return "/api/v1/management/devices" + path.substring("/devices".length());
        }
        if (path.equals("/maps") || path.startsWith("/maps/")) {
            return "/api/v1/management/maps" + path.substring("/maps".length());
        }
        throw new ResponseStatusException(NOT_FOUND, "Unsupported bigscreen business API: " + requestPath);
    }
}
