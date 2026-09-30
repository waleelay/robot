package com.robot.bigscreen.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;

import com.robot.bigscreen.client.CenterProxyClient;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 提供大屏通用代理及当前访问上下文入口，具体权限由安全配置限定。 */
@RestController
public class BigscreenProxyController {

    private final CenterProxyClient proxyClient;

    /**
     * 初始化 BigscreenProxyController，保存所需依赖及初始运行状态。
     *
     * @param proxyClient 将浏览器路径映射到 Management 或 Control，并转发请求和响应。
     */
    public BigscreenProxyController(CenterProxyClient proxyClient) {
        this.proxyClient = proxyClient;
    }

    /**
     * 返回已移除机器人列表接口的迁移提示；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @return 返回已移除机器人列表接口的迁移提示的接口响应
     */
    @Operation(
            operationId = "bigscreenProxyController_removedRobots",
            summary = "返回已移除机器人列表接口的迁移提示",
            description = "返回已移除机器人列表接口的迁移提示。浏览器身份由 BFF JWT 安全配置检查，聚合结果可带数据质量标识。",
            tags = {"BigscreenProxyController"})
    @ApiResponse(
            responseCode = "410",
            description = "入口已移除",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @RequestMapping("/api/control/robots")
    public ResponseEntity<Map<String, Object>> removedRobots() {
        return ResponseEntity.status(HttpStatus.GONE).body(Map.of(
                "code", "API_REMOVED",
                "message", "Use /api/bigscreen/panorama/overview instead of /api/control/robots."));
    }

    /**
     * 透传 Management 的当前用户访问上下文；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param request 请求参数
     * @return 透传 Management 的当前用户访问上下文的接口响应
     */
    @Operation(
            operationId = "bigscreenProxyController_currentAccess",
            summary = "透传 Management 的当前用户访问上下文",
            description = "透传 Management 的当前用户访问上下文。浏览器身份由 BFF JWT 安全配置检查，聚合结果可带数据质量标识。",
            tags = {"BigscreenProxyController"})
    @ApiResponse(
            responseCode = "200",
            description = "响应正文；下载接口保留 Content-Type 与 Content-Disposition",
            content = @Content(mediaType = "*/*", schema = @Schema(type = "string", format = "binary")))
    @GetMapping("/api/bigscreen/access-control/me")
    public ResponseEntity<byte[]> currentAccess(HttpServletRequest request) {
        return proxyClient.forwardToManage(request, "/api/v1/management/access-control/me");
    }

    /**
     * 接收通配代理请求并按既有路由转发，身份头由受信上下文生成。
     *
     * @param request 请求参数
     * @return 下游返回的 HTTP 状态、正文及代理保留的响应头
     */
    @io.swagger.v3.oas.annotations.Hidden
    @RequestMapping({
            "/api/control/**",
            "/api/bigscreen/**",
            "/api/media/**",
            "/api/manage/**",
            "/api/v1/management/**"
    })
    public ResponseEntity<byte[]> forward(HttpServletRequest request) {
        return proxyClient.forward(request);
    }
}
