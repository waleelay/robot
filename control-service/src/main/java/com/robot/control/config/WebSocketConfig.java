package com.robot.control.config;

import com.robot.control.ws.MediaWebSocketHandler;
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

    /**
     * 创建 WebSocketConfig 实例。
     *
     * @param mediaWebSocketHandler WebSocket 处理器
     */
    public WebSocketConfig(MediaWebSocketHandler mediaWebSocketHandler) {
        this.mediaWebSocketHandler = mediaWebSocketHandler;
    }

    /**
     * 注册 Control Service WebSocket 端点。
     *
     * @param registry 格式化注册表
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(mediaWebSocketHandler, "/ws/media", "/ws/control")
                .setAllowedOriginPatterns("*");
    }
}
