package com.robot.mediaserver.control.config;

import com.robot.mediaserver.control.ws.MediaWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final MediaWebSocketHandler mediaWebSocketHandler;

    public WebSocketConfig(MediaWebSocketHandler mediaWebSocketHandler) {
        this.mediaWebSocketHandler = mediaWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(mediaWebSocketHandler, "/ws/media", "/ws/control")
                .setAllowedOriginPatterns("*");
    }
}
