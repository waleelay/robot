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

    public static final String HTTP_HEADERS_ATTR = "mediaWsHttpHeaders";

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
        // no-op
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

    @SuppressWarnings("unchecked")
    public static Map<String, String> headers(org.springframework.web.socket.WebSocketSession session) {
        Object value = session.getAttributes().get(HTTP_HEADERS_ATTR);
        if (value instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, String>) map);
        }
        return Map.of();
    }
}
