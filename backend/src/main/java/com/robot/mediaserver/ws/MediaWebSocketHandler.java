package com.robot.mediaserver.ws;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.mediaserver.auth.CurrentUser;
import com.robot.mediaserver.control.service.EquipmentControlService;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
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
    private final ObjectMapper objectMapper;
    private final EquipmentControlService equipmentControlService;

    public MediaWebSocketHandler(
            MediaWebSocketPublisher publisher,
            ObjectMapper objectMapper,
            EquipmentControlService equipmentControlService) {
        this.publisher = publisher;
        this.objectMapper = objectMapper;
        this.equipmentControlService = equipmentControlService;
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
     * 处理前端通过 /ws/control 发送的高频控制帧。
     *
     * @param session WebSocket 会话
     * @param message 前端消息
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Map<String, Object> incoming = objectMapper.readValue(message.getPayload(), new TypeReference<>() {});
        if (!"control.command".equals(incoming.get("type"))) {
            return;
        }
        String requestId = stringValue(incoming.get("requestId"), "");
        try {
            Map<String, Object> payload = mapValue(incoming.get("payload"));
            String robotId = stringValue(payload.get("robotId"), "");
            Map<String, Object> result = equipmentControlService.publishCommand(robotId, payload, currentUser(session));
            send(session, "control.command.accepted", requestId, result);
        } catch (Exception ex) {
            send(session, "control.command.rejected", requestId, object(
                    "code", "CONTROL_COMMAND_REJECTED",
                    "message", ex.getMessage()));
        }
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

    private CurrentUser currentUser(WebSocketSession session) {
        String userId = headerOrDefault(session, "X-User-Id", "u1001");
        String orgId = headerOrDefault(session, "X-Org-Id", "org001");
        String clientId = headerOrDefault(session, "X-Client-Id", session.getId());
        return new CurrentUser(userId, orgId, java.util.Set.of("MEDIA_OPERATOR", "EQUIPMENT_OPERATOR"), clientId);
    }

    private String headerOrDefault(WebSocketSession session, String name, String defaultValue) {
        String value = session.getHandshakeHeaders().getFirst(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private void send(WebSocketSession session, String type, String requestId, Object payload) throws IOException {
        Map<String, Object> body = object(
                "type", type,
                "requestId", requestId,
                "timestamp", OffsetDateTime.now().toString(),
                "payload", payload);
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(body)));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> map ? new LinkedHashMap<>((Map<String, Object>) map) : new LinkedHashMap<>();
    }

    private static String stringValue(Object value, String defaultValue) {
        return value == null || String.valueOf(value).isBlank() ? defaultValue : String.valueOf(value);
    }

    private static Map<String, Object> object(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length - 1; i += 2) {
            map.put(String.valueOf(values[i]), values[i + 1]);
        }
        return map;
    }
}
