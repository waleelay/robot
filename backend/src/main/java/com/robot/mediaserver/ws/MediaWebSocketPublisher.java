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

/** 管理媒体 WebSocket 连接并向订阅者发布事件和二进制音频。 */
@Component
public class MediaWebSocketPublisher {

    private static final Logger log = LoggerFactory.getLogger(MediaWebSocketPublisher.class);

    private final ObjectMapper objectMapper;
    /**
     * 当前实例的媒体 WebSocket 广播连接集合；关闭或发送失败时移除。
     */
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    /**
     * 初始化 MediaWebSocketPublisher，保存所需依赖及初始运行状态。
     *
     * @param objectMapper JSON 编解码器
     */
    public MediaWebSocketPublisher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 登记浏览器 WebSocket 连接，供后续事件或二进制广播使用。
     *
     * @param session WebSocket 会话
     */
    public void addSession(WebSocketSession session) {
        sessions.add(session);
    }

    /**
     * 移除已关闭连接，避免后续广播继续占用会话资源。
     *
     * @param session WebSocket 会话
     */
    public void removeSession(WebSocketSession session) {
        sessions.remove(session);
    }

    /**
     * 向当前可用连接广播媒体业务事件，单连接失败不阻断其余接收方。
     *
     * @param event 事件名称
     * @param data 业务数据
     */
    public void publish(String event, Object data) {
        Map<String, Object> payload = Map.of(
                "event", event,
                "timestamp", DateTimeConfig.format(OffsetDateTime.now()),
                "data", data);
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("序列化 WebSocket 消息失败", ex);
        }
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                sessions.remove(session);
                continue;
            }
            try {
                session.sendMessage(new TextMessage(json));
            } catch (IOException ex) {
                log.warn("发送 WebSocket 事件失败 事件={}", event, ex);
                sessions.remove(session);
            }
        }
    }

    /**
     * 向当前可用连接广播音频等二进制数据。
     *
     * @param bytes 内容的原始二进制字节
     */
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
                log.warn("发送 WebSocket 二进制消息失败", ex);
                sessions.remove(session);
            }
        }
    }
}
