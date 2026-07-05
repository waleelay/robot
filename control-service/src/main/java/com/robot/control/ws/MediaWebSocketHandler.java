package com.robot.control.ws;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.control.auth.CurrentUser;
import com.robot.control.config.DateTimeConfig;
import com.robot.control.service.EquipmentControlService;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Control Service 前端 WebSocket 连接处理器。
 *
 * @author leelay
 * @date 2026-07-05
 */
@Component
public class MediaWebSocketHandler extends TextWebSocketHandler {

    private final MediaWebSocketPublisher publisher;
    private final ObjectMapper objectMapper;
    private final EquipmentControlService equipmentControlService;

    /**
     * 创建 MediaWebSocketHandler 实例。
     *
     * @param publisher publisher
     * @param objectMapper JSON 编解码器
     * @param equipmentControlService 装备控制服务
     */
    public MediaWebSocketHandler(
            MediaWebSocketPublisher publisher,
            ObjectMapper objectMapper,
            EquipmentControlService equipmentControlService) {
        this.publisher = publisher;
        this.objectMapper = objectMapper;
        this.equipmentControlService = equipmentControlService;
    }

    /**
     * 处理前端 WebSocket 建连。
     *
     * @param session WebSocket 会话
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        publisher.addSession(session);
    }

    /**
     * 处理前端发送的 WebSocket 文本消息。
     *
     * @param session WebSocket 会话
     * @param message 消息内容
     * @throws Exception Exception 处理失败时抛出
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
     * 处理前端 WebSocket 断连。
     *
     * @param session WebSocket 会话
     * @param status 状态消息
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        publisher.removeSession(session);
    }

    /**
     * 从 WebSocket 会话解析当前用户。
     *
     * @param session WebSocket 会话
     * @return 当前用户
     */
    private CurrentUser currentUser(WebSocketSession session) {
        String userId = headerOrDefault(session, "X-User-Id", "u1001");
        String orgId = headerOrDefault(session, "X-Org-Id", "org001");
        String clientId = headerOrDefault(session, "X-Client-Id", session.getId());
        return new CurrentUser(userId, orgId, Set.of("MEDIA_OPERATOR", "EQUIPMENT_OPERATOR"), clientId);
    }

    /**
     * 读取请求头，缺省时返回默认值。
     *
     * @param session WebSocket 会话
     * @param name 名称
     * @param defaultValue 默认值
     * @return 请求头值或默认值
     */
    private String headerOrDefault(WebSocketSession session, String name, String defaultValue) {
        String value = session.getHandshakeHeaders().getFirst(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    /**
     * 向单个 WebSocket 会话发送消息。
     *
     * @param session WebSocket 会话
     * @param type type
     * @param requestId requestId
     * @param payload 消息载荷
     * @throws IOException IOException 处理失败时抛出
     */
    private void send(WebSocketSession session, String type, String requestId, Object payload) throws IOException {
        Map<String, Object> body = object(
                "type", type,
                "requestId", requestId,
                "timestamp", DateTimeConfig.format(OffsetDateTime.now()),
                "payload", payload);
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(body)));
    }

    /**
     * 将对象转换为 Map。
     *
     * @param value 待处理值
     * @return Map 值
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> map ? new LinkedHashMap<>((Map<String, Object>) map) : new LinkedHashMap<>();
    }

    /**
     * 读取字符串值并应用默认值。
     *
     * @param value 待处理值
     * @param defaultValue 默认值
     * @return 字符串值
     */
    private static String stringValue(Object value, String defaultValue) {
        return value == null || String.valueOf(value).isBlank() ? defaultValue : String.valueOf(value);
    }

    /**
     * 按键值对构造 Map。
     *
     * @param values 键值对数组
     * @return Map 对象
     */
    private static Map<String, Object> object(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length - 1; i += 2) {
            map.put(String.valueOf(values[i]), values[i + 1]);
        }
        return map;
    }
}
