package com.robot.bigscreen.config;

import com.robot.bigscreen.ws.BigscreenWebSocketBridgeHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
@EnableScheduling
public class WebSocketConfig implements WebSocketConfigurer {

    public static final int MAX_TEXT_MESSAGE_SIZE = 256 * 1024;

    private final BigscreenWebSocketBridgeHandler bridgeHandler;

    public WebSocketConfig(BigscreenWebSocketBridgeHandler bridgeHandler) {
        this.bridgeHandler = bridgeHandler;
    }

    @Bean
    public ServletServerContainerFactoryBean webSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(MAX_TEXT_MESSAGE_SIZE);
        container.setMaxBinaryMessageBufferSize(MAX_TEXT_MESSAGE_SIZE);
        return container;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(bridgeHandler, "/ws/control", "/ws/media", "/ws/bigscreen")
                .setAllowedOriginPatterns("*");
    }
}
