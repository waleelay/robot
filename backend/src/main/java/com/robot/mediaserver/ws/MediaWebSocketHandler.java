package com.robot.mediaserver.ws;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
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
     * 当前阶段前端只订阅状态，暂不处理客户端消息。
     *
     * @param session WebSocket 会话
     * @param message 前端消息
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 后续可在这里扩展按 sessionId、robotId 过滤订阅。
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
