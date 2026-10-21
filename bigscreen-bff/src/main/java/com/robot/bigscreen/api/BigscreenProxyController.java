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

/** 提供大屏通用代理、旧入口迁移提示及当前访问上下文入口，访问权限由安全配置限定。 */
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
     * 拦截旧机器人列表入口并返回迁移提示，避免请求落入通配代理转发到 Control。
     *
     * @return 包含替代入口说明的 410 响应
     */
    @Operation(
            operationId = "bigscreenProxyController_removedRobots",
            summary = "返回已移除机器人列表接口的迁移提示",
            description = "请求命中旧入口时返回 410 和替代入口说明，不访问下游 Control；浏览器身份由 BFF JWT 安全配置检查。",
            tags = {"BigscreenProxyController"})
    @ApiResponse(
            responseCode = "410",
            description = "入口已移除",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    // 保留不限定 HTTP 方法的精确映射，避免其他方法落入通配代理。
    @RequestMapping("/api/control/robots")
    public ResponseEntity<Map<String, Object>> removedRobots() {
        return ResponseEntity.status(HttpStatus.GONE).body(Map.of(
                "code", "API_REMOVED",
                "message", "Use /api/bigscreen/panorama/overview instead of /api/control/robots."));
    }

    /**
     * 将当前用户访问上下文请求定向转发到 Management，保留下游响应且不提供默认权限。
     *
     * @param request 携带已认证浏览器身份的请求
     * @return Management 返回的 HTTP 状态、响应头和正文
     */
    @Operation(
            operationId = "bigscreenProxyController_currentAccess",
            summary = "透传 Management 的当前用户访问上下文",
            description = "将当前用户访问上下文请求转发到 Management；浏览器身份由 BFF JWT 安全配置检查，下游失败时不返回默认权限。",
            tags = {"BigscreenProxyController"})
    @ApiResponse(
            responseCode = "200",
            description = "Management 返回当前用户访问上下文",
            content = @Content(mediaType = "application/json", schema = @Schema(ref = "#/components/schemas/CurrentAccess")))
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
