package com.robot.bigscreen.config;

import com.robot.bigscreen.ws.BigscreenWebSocketBridgeHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@EnableScheduling
public class WebSocketConfig implements WebSocketConfigurer {

    private final BigscreenWebSocketBridgeHandler bridgeHandler;

    public WebSocketConfig(BigscreenWebSocketBridgeHandler bridgeHandler) {
        this.bridgeHandler = bridgeHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(bridgeHandler, "/ws/control", "/ws/media", "/ws/bigscreen")
                .setAllowedOriginPatterns("*");
    }
}
