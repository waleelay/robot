package com.robot.control.auth;

import com.robot.control.config.ControlServiceProperties;
import com.robot.control.ws.MediaWsAuthHandshakeInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 从 HTTP 请求头解析当前用户上下文。
 *
 * @author leelay
 * @date 2026-07-05
 */
@Component
public class CurrentUserResolver {

    private final ControlServiceProperties properties;

    /**
     * 创建 CurrentUserResolver 实例。
     *
     * @param properties 服务配置
     */
    public CurrentUserResolver(ControlServiceProperties properties) {
        this.properties = properties;
    }

    /**
     * 从请求头解析当前用户。
     *
     * @param request 请求参数
     * @return 当前用户上下文
     */
    public CurrentUser resolve(HttpServletRequest request) {
        String userId = trustedHeaderOrDefault("X-User-Id", request.getHeader("X-User-Id"), "dev-user");
        String orgId = headerOrDefault(request, "X-Org-Id", properties.getAuth().getDefaultOrgId());
        String clientId = headerOrDefault(request, "X-Client-Id", "web");
        String rolesHeader = trustedHeaderOrDefault(
                "X-Roles",
                request.getHeader("X-Roles"),
                "MEDIA_VIEWER,MEDIA_OPERATOR,EQUIPMENT_OPERATOR");
        return currentUser(userId, orgId, clientId, rolesHeader);
    }

    /**
     * 从 WebSocket 握手信息解析当前用户。
     *
     * <p>浏览器不能自行设置 WebSocket 请求头，因此 clientId 允许通过查询参数传入。
     * 用户和角色仍优先读取网关注入的请求头。</p>
     *
     * @param session WebSocket 会话
     * @return 当前用户上下文
     */
    public CurrentUser resolve(WebSocketSession session) {
        Map<String, String> headers = MediaWsAuthHandshakeInterceptor.headers(session);
        String userId = trustedHeaderOrDefault("X-User-Id", headers.get("X-User-Id"), "dev-user");
        String orgId = headerOrDefault(headers, "X-Org-Id", properties.getAuth().getDefaultOrgId());
        String clientId = headerOrDefault(
                headers,
                "X-Client-Id",
                queryOrDefault(session, "clientId", session.getId()));
        String rolesHeader = trustedHeaderOrDefault(
                "X-Roles",
                headers.get("X-Roles"),
                "MEDIA_VIEWER,MEDIA_OPERATOR,EQUIPMENT_OPERATOR");
        return currentUser(userId, orgId, clientId, rolesHeader);
    }

    private CurrentUser currentUser(String userId, String orgId, String clientId, String rolesHeader) {
        Set<String> roles = Arrays.stream(rolesHeader.split(","))
                .map(String::trim)
                .filter(role -> !role.isBlank())
                .collect(Collectors.toSet());
        return new CurrentUser(userId, orgId, roles, clientId);
    }

    /**
     * 读取请求头，缺省时返回默认值。
     *
     * @param request 请求参数
     * @param name 名称
     * @param defaultValue 默认值
     * @return 请求头值或默认值
     */
    private String headerOrDefault(HttpServletRequest request, String name, String defaultValue) {
        String value = request.getHeader(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private String headerOrDefault(Map<String, String> headers, String name, String defaultValue) {
        String value = headers.get(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private String trustedHeaderOrDefault(String name, String value, String defaultValue) {
        if (value != null && !value.isBlank()) {
            return value;
        }
        if (properties.getAuth().isAllowDefaultUser()) {
            return defaultValue;
        }
        throw new IllegalArgumentException("缺少可信身份头：" + name);
    }

    private String queryOrDefault(WebSocketSession session, String name, String defaultValue) {
        if (session.getUri() == null) {
            return defaultValue;
        }
        String value = UriComponentsBuilder.fromUri(session.getUri())
                .build()
                .getQueryParams()
                .getFirst(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
