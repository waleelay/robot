package com.robot.control.config;

import com.robot.control.ws.MediaWebSocketHandler;
import com.robot.control.ws.MediaWsAuthHandshakeInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Control Service WebSocket 端点配置。
 *
 * @author leelay
 * @date 2026-07-05
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final MediaWebSocketHandler mediaWebSocketHandler;
    private final FieldCallWebSocketHandler fieldCallWebSocketHandler;
    private final MediaWsAuthHandshakeInterceptor authHandshakeInterceptor;

    public WebSocketConfig(
            MediaWebSocketHandler mediaWebSocketHandler,
            FieldCallWebSocketHandler fieldCallWebSocketHandler,
            MediaWsAuthHandshakeInterceptor authHandshakeInterceptor) {
        this.mediaWebSocketHandler = mediaWebSocketHandler;
        this.fieldCallWebSocketHandler = fieldCallWebSocketHandler;
        this.authHandshakeInterceptor = authHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(mediaWebSocketHandler, "/ws/media", "/ws/control")
                .setAllowedOriginPatterns("*")
                .addInterceptors(authHandshakeInterceptor);
        registry.addHandler(fieldCallWebSocketHandler, "/ws/field-call")
                .setAllowedOriginPatterns("*")
                .addInterceptors(authHandshakeInterceptor);
    }
}
