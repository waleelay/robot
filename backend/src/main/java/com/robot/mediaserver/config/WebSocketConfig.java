package com.robot.mediaserver.config;

import com.robot.mediaserver.ws.MediaWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 媒体业务状态 WebSocket 配置。
 *
 * @author leelay
 * @date 2026/05/19
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final MediaWebSocketHandler mediaWebSocketHandler;

    public WebSocketConfig(MediaWebSocketHandler mediaWebSocketHandler) {
        this.mediaWebSocketHandler = mediaWebSocketHandler;
    }

    /**
     * 注册媒体状态 WebSocket 入口。
     *
     * @param registry WebSocket handler 注册器
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(mediaWebSocketHandler, "/ws/media")
                .setAllowedOriginPatterns("*");
    }
}
