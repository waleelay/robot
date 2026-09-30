package com.robot.control.config;

import com.robot.control.ws.FieldCallWebSocketHandler;
import com.robot.control.ws.MediaWebSocketHandler;
import com.robot.control.ws.MediaWsAuthHandshakeInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 控制服务 WebSocket 端点配置。
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

    /**
     * 初始化 WebSocketConfig，保存所需依赖及初始运行状态。
     *
     * @param mediaWebSocketHandler 控制服务 前端 WebSocket 连接处理器。
     * @param fieldCallWebSocketHandler 现场应用 WebSocket 信令（/ws/field-call）。
     * @param authHandshakeInterceptor 保存 WebSocket 握手时的认证头快照，供指令处理线程透传下游认证信息。
     */
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
