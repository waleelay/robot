package com.robot.bigscreen.api;


import com.robot.bigscreen.client.CenterProxyClient;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/** 为业务任务提供明确代理路径，保留通用代理与专用路由的优先级。 */
@RestController
public class BusinessTaskProxyController {

    private static final String BUSINESS_PREFIX = "/api/bigscreen/business";

    private final CenterProxyClient proxyClient;

    /**
     * 初始化 BusinessTaskProxyController，保存所需依赖及初始运行状态。
     *
     * @param proxyClient 将浏览器路径映射到 Management 或 Control，并转发请求和响应。
     */
    public BusinessTaskProxyController(CenterProxyClient proxyClient) {
        this.proxyClient = proxyClient;
    }

    /**
     * 将业务白名单路径映射到 Management，禁止通过通配入口构造任意下游路径。
     *
     * @param request 请求参数
     * @return Management 的 HTTP 响应
     */
    @io.swagger.v3.oas.annotations.Hidden
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
        if (path.equals("/selection-options") || path.startsWith("/selection-options/")) {
            return "/api/v1/management/selection-options" + path.substring("/selection-options".length());
        }
        if (path.equals("/external/temporary-navigations")) {
            return "/api/v1/management/external/temporary-navigations";
        }
        if (path.equals("/external/service-point-navigations")) {
            return "/api/v1/management/external/service-point-navigations";
        }
        String servicePointPrefix = "/external/devices/";
        String servicePointSuffix = "/service-point-options";
        if (path.startsWith(servicePointPrefix) && path.endsWith(servicePointSuffix)) {
            String serialNumber = path.substring(
                    servicePointPrefix.length(), path.length() - servicePointSuffix.length());
            if (!serialNumber.isBlank() && !serialNumber.contains("/")) {
                return "/api/v1/management" + path;
            }
        }
        throw new ResponseStatusException(NOT_FOUND, "Unsupported bigscreen business API: " + requestPath);
    }
}
