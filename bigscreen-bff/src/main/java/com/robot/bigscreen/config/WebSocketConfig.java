package com.robot.bigscreen.config;

import com.robot.bigscreen.ws.BigscreenWebSocketBridgeHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/** 注册大屏和兼容媒体、控制通道的 WebSocket 桥接处理器。 */
@Configuration
@EnableWebSocket
@EnableScheduling
public class WebSocketConfig implements WebSocketConfigurer {

    /**
     * 容器允许缓冲的单条文本或二进制消息最大字节数。
     */
    public static final int MAX_TEXT_MESSAGE_SIZE = 256 * 1024;

    private final BigscreenWebSocketBridgeHandler bridgeHandler;

    /**
     * 初始化 WebSocketConfig，保存所需依赖及初始运行状态。
     *
     * @param bridgeHandler 维护浏览器与下游实时连接，并处理授权刷新、重连和会话释放。
     */
    public WebSocketConfig(BigscreenWebSocketBridgeHandler bridgeHandler) {
        this.bridgeHandler = bridgeHandler;
    }

    /**
     * 配置 WebSocket 容器的消息大小与传输约束。
     *
     * @return 由容器管理的 WebSocket 配置工厂
     */
    @Bean
    public ServletServerContainerFactoryBean webSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(MAX_TEXT_MESSAGE_SIZE);
        container.setMaxBinaryMessageBufferSize(MAX_TEXT_MESSAGE_SIZE);
        return container;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(bridgeHandler, "/ws/control", "/ws/media", "/ws/bigscreen", "/ws/field-call")
                .setAllowedOriginPatterns("*");
    }
}
