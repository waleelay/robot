package com.robot.mediaserver.ws;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 媒体业务状态 WebSocket 处理器。
 *
 * @author leelay
 * @date 2026/05/19
 */
@Component
public class MediaWebSocketHandler extends TextWebSocketHandler {

    private final MediaWebSocketPublisher publisher;

    /**
     * 初始化 MediaWebSocketHandler，保存所需依赖及初始运行状态。
     *
     * @param publisher 向目标浏览器或身份分组投递消息的回调
     */
    public MediaWebSocketHandler(MediaWebSocketPublisher publisher) {
        this.publisher = publisher;
    }

    /**
     * 建立连接后加入广播集合。
     *
     * @param session WebSocket 会话
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        publisher.addSession(session);
    }

    /**
     * 连接关闭后移除广播集合。
     *
     * @param session WebSocket 会话
     * @param status 关闭状态
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        publisher.removeSession(session);
    }
}
