package com.robot.control.ws;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * 保存 WebSocket 握手时的认证头快照，供指令处理线程透传下游认证信息。
 * 这样下游透传器（如 {@link com.robot.control.auth.RequestAuthorizationHeaders}）
 * 在 WebSocket 指令线程也能读到握手请求携带的 Bearer Token，避免管理端 401。
 */
@Component
public class MediaWsAuthHandshakeInterceptor implements HandshakeInterceptor {

    /**
     * 握手认证头快照在 WebSocket 属性中的存储键。
     */
    public static final String HTTP_HEADERS_ATTR = "mediaWsHttpHeaders";

    /**
     * 只保存握手请求头，不独立验证令牌；身份可信性依赖受控上游认证与内部入口隔离。
     */
    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
        Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            attributes.put(HTTP_HEADERS_ATTR, headers(servletRequest.getServletRequest()));
        }
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        // 身份头已在握手前保存，握手完成后无需额外处理。
    }

    private static Map<String, String> headers(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        copyHeader(request, headers, "Authorization");
        copyHeader(request, headers, "X-User-Id");
        copyHeader(request, headers, "X-Org-Id");
        copyHeader(request, headers, "X-Roles");
        copyHeader(request, headers, "X-Client-Id");
        return headers;
    }

    private static void copyHeader(HttpServletRequest request, Map<String, String> headers, String name) {
        String value = request.getHeader(name);
        if (value != null && !value.isBlank()) {
            headers.put(name, value);
        }
    }

    /**
     * 从 WebSocket 握手属性读取已经保存的可信请求头。
     *
     * @param session WebSocket 会话
     * @return 本连接握手时的请求头
     */
    @SuppressWarnings("unchecked")
    public static Map<String, String> headers(org.springframework.web.socket.WebSocketSession session) {
        Object value = session.getAttributes().get(HTTP_HEADERS_ATTR);
        if (value instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, String>) map);
        }
        return Map.of();
    }
}
