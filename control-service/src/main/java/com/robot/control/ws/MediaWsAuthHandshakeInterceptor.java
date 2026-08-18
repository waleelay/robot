package com.robot.control.ws;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * 保存 WebSocket 握手时的 HTTP 请求，供指令处理线程恢复 Servlet 请求上下文。
 * 这样下游透传器（如 {@link com.robot.control.auth.RequestAuthorizationHeaders}）
 * 在 WebSocket 指令线程也能读到握手请求携带的 Bearer Token，避免管理端 401。
 */
@Component
public class MediaWsAuthHandshakeInterceptor implements HandshakeInterceptor {

    public static final String HTTP_REQUEST_ATTR = "mediaWsHttpServletRequest";

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            attributes.put(HTTP_REQUEST_ATTR, servletRequest.getServletRequest());
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

    public static HttpServletRequest request(org.springframework.web.socket.WebSocketSession session) {
        Object value = session.getAttributes().get(HTTP_REQUEST_ATTR);
        return value instanceof HttpServletRequest request ? request : null;
    }
}
