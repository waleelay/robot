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
    private final MediaWsAuthHandshakeInterceptor authHandshakeInterceptor;

    /**
     * 创建 WebSocketConfig 实例。
     *
     * @param mediaWebSocketHandler WebSocket 处理器
     * @param authHandshakeInterceptor 保存握手请求的拦截器
     */
    public WebSocketConfig(
            MediaWebSocketHandler mediaWebSocketHandler,
            MediaWsAuthHandshakeInterceptor authHandshakeInterceptor) {
        this.mediaWebSocketHandler = mediaWebSocketHandler;
        this.authHandshakeInterceptor = authHandshakeInterceptor;
    }

    /**
     * 注册 Control Service WebSocket 端点。
     *
     * @param registry 格式化注册表
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(mediaWebSocketHandler, "/ws/media", "/ws/control")
                .setAllowedOriginPatterns("*")
                .addInterceptors(authHandshakeInterceptor);
    }
}
