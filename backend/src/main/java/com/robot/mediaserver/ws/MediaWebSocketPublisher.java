package com.robot.mediaserver.ws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.mediaserver.config.DateTimeConfig;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Component
public class MediaWebSocketPublisher {

    private static final Logger log = LoggerFactory.getLogger(MediaWebSocketPublisher.class);

    private final ObjectMapper objectMapper;
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    public MediaWebSocketPublisher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void addSession(WebSocketSession session) {
        sessions.add(session);
    }

    public void removeSession(WebSocketSession session) {
        sessions.remove(session);
    }

    public void publish(String event, Object data) {
        Map<String, Object> payload = Map.of(
                "event", event,
                "timestamp", DateTimeConfig.format(OffsetDateTime.now()),
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

    public void publishBinary(byte[] bytes) {
        BinaryMessage message = new BinaryMessage(bytes);
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                sessions.remove(session);
                continue;
            }
            try {
                session.sendMessage(message);
            } catch (IOException ex) {
                log.warn("Failed to send websocket binary message", ex);
                sessions.remove(session);
            }
        }
    }
}
