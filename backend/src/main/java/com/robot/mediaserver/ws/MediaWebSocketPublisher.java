package com.robot.mediaserver.ws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * 媒体业务状态 WebSocket 广播器。
 *
 * <p>WebSocket 只承载会话状态、抓拍状态等业务事件，不承载实时音视频流。</p>
 *
 * @author leelay
 * @date 2026/05/19
 */
@Component
public class MediaWebSocketPublisher {

    private static final Logger log = LoggerFactory.getLogger(MediaWebSocketPublisher.class);

    private final ObjectMapper objectMapper;
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    public MediaWebSocketPublisher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 加入 WebSocket 会话。
     *
     * @param session WebSocket 会话
     */
    public void addSession(WebSocketSession session) {
        sessions.add(session);
    }

    /**
     * 移除 WebSocket 会话。
     *
     * @param session WebSocket 会话
     */
    public void removeSession(WebSocketSession session) {
        sessions.remove(session);
    }

    /**
     * 广播媒体业务事件。
     *
     * @param event 事件名称
     * @param data 事件数据
     */
    public void publish(String event, Object data) {
        Map<String, Object> payload = Map.of(
                "event", event,
                "timestamp", OffsetDateTime.now().toString(),
                "data", data);
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize websocket payload", ex);
        }
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                sessions.remove(session);
                continue;
            }
            try {
                session.sendMessage(new TextMessage(json));
            } catch (IOException ex) {
                log.warn("Failed to send websocket event={}", event, ex);
                sessions.remove(session);
            }
        }
    }
}
