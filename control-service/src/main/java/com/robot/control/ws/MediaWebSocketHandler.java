package com.robot.control.ws;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.control.auth.CurrentUser;
import com.robot.control.auth.CurrentUserResolver;
import com.robot.control.auth.RequestAuthorizationHeaders;
import com.robot.control.call.FieldCallService;
import com.robot.control.call.IntercomCallService;
import com.robot.control.client.ControlManagementClient;
import com.robot.control.config.DateTimeConfig;
import com.robot.control.service.EquipmentControlService;
import com.robot.control.trajectory.TrajectoryCoordinator;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
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

    private static final Logger log = LoggerFactory.getLogger(MediaWebSocketHandler.class);
    private static final Pattern SAFE_TRACE_ID = Pattern.compile("[A-Za-z0-9._-]{1,128}");

    private final MediaWebSocketPublisher publisher;
    private final ObjectMapper objectMapper;
    private final EquipmentControlService equipmentControlService;
    private final IntercomCallService intercomCallService;
    private final FieldCallService fieldCallService;
    private final CurrentUserResolver currentUserResolver;
    private final RequestAuthorizationHeaders requestAuthorizationHeaders;
    private final ControlManagementClient managementClient;
    private final TrajectoryCoordinator trajectoryCoordinator;
    private final Map<String, CurrentUser> usersBySession = new ConcurrentHashMap<>();

    public MediaWebSocketHandler(
            MediaWebSocketPublisher publisher,
            ObjectMapper objectMapper,
            EquipmentControlService equipmentControlService,
            IntercomCallService intercomCallService,
            FieldCallService fieldCallService,
            CurrentUserResolver currentUserResolver,
            RequestAuthorizationHeaders requestAuthorizationHeaders,
            ControlManagementClient managementClient,
            TrajectoryCoordinator trajectoryCoordinator) {
        this.publisher = publisher;
        this.objectMapper = objectMapper;
        this.equipmentControlService = equipmentControlService;
        this.intercomCallService = intercomCallService;
        this.fieldCallService = fieldCallService;
        this.currentUserResolver = currentUserResolver;
        this.requestAuthorizationHeaders = requestAuthorizationHeaders;
        this.managementClient = managementClient;
        this.trajectoryCoordinator = trajectoryCoordinator;
    }

    /**
     * 处理前端 WebSocket 建连。
     *
     * @param session WebSocket 会话
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        CurrentUser user = currentUser(session);
        publisher.addSession(session);
        usersBySession.put(session.getId(), user);
        requestAuthorizationHeaders.setWebSocketHeaders(MediaWsAuthHandshakeInterceptor.headers(session));
        try {
            managementClient.warmCurrentUserDeviceCache();
        } catch (RuntimeException exception) {
            // 档案预热失败不能中断实时 WebSocket，后续请求或重连仍可重新加载。
            log.warn("预热当前用户设备档案失败，WebSocket 会话={}", session.getId(), exception);
        } finally {
            requestAuthorizationHeaders.clearWebSocketHeaders();
        }
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
        String type = stringValue(incoming.get("type"), "");
        String requestId = stringValue(incoming.get("requestId"), "");
        String traceId = SAFE_TRACE_ID.matcher(requestId).matches() ? requestId : "ws-" + session.getId();
        MDC.put("traceId", traceId);
        requestAuthorizationHeaders.setWebSocketHeaders(MediaWsAuthHandshakeInterceptor.headers(session));
        try {
            Map<String, Object> payload = mapValue(incoming.get("payload"));
            switch (type) {
                case "control.command" -> {
                    String robotId = stringValue(payload.get("robotId"), "");
                    Map<String, Object> result = equipmentControlService.publishCommand(robotId, payload, currentUser(session));
                    send(session, "control.command.accepted", requestId, result);
                }
                case "video.intercom.call.accept" -> {
                    String callId = stringValue(payload.get("callId"), "");
                    requireAuthorizedCall(callId);
                    send(session, "video.intercom.call.accepted", requestId,
                            intercomCallService.accept(callId, currentUser(session)));
                }
                case "video.intercom.call.reject" -> {
                    String callId = stringValue(payload.get("callId"), "");
                    requireAuthorizedCall(callId);
                    send(session, "video.intercom.call.rejected", requestId,
                            intercomCallService.reject(callId, currentUser(session)));
                }
                case "video.intercom.call.query" -> send(
                        session, "video.intercom.call.list", requestId, authorizedRingingCalls());
                case "video.field.call.accept" -> {
                    String callId = stringValue(payload.get("callId"), "");
                    send(session, "video.field.call.accepted", requestId,
                            fieldCallService.accept(callId, currentUser(session)));
                }
                case "video.field.call.reject" -> {
                    String callId = stringValue(payload.get("callId"), "");
                    send(session, "video.field.call.rejected", requestId,
                            fieldCallService.reject(callId, currentUser(session)));
                }
                case "video.field.call.hangup" -> {
                    String callId = stringValue(payload.get("callId"), "");
                    fieldCallService.hangup(callId, currentUser(session), "center-hangup");
                    send(session, "video.field.call.ended", requestId, Map.of("callId", callId));
                }
                case "video.field.call.query" -> send(
                        session, "video.field.call.list", requestId, fieldCallService.ringingCalls());
                case "trajectory.watch.sync" -> {
                    try {
                        trajectoryCoordinator.sync(session, payload);
                    } catch (IllegalArgumentException exception) {
                        log.warn("已忽略非法轨迹观看集合，WebSocket 会话={} 原因={}",
                                session.getId(), exception.getMessage());
                    }
                }
                default -> send(session, rejectedType(type), requestId, object(
                        "code", "UNSUPPORTED_MESSAGE_TYPE",
                        "message", "不支持的 WebSocket 消息类型：" + type));
            }
        } catch (Exception ex) {
            send(session, rejectedType(type), requestId, object(
                    "code", "OPERATION_REJECTED",
                    "message", ex.getMessage()));
        } finally {
            requestAuthorizationHeaders.clearWebSocketHeaders();
            MDC.remove("traceId");
        }
    }

    private String rejectedType(String type) {
        if (type.startsWith("video.field.call.")) {
            return "video.field.call.operation-failed";
        }
        if (type.startsWith("video.intercom.call.")) {
            return "video.intercom.call.operation-failed";
        }
        return "control.command.rejected";
    }

    /**
     * 处理前端 WebSocket 断连。
     *
     * @param session WebSocket 会话
     * @param status 状态消息
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        CurrentUser user = usersBySession.remove(session.getId());
        try {
            if (user != null && usersBySession.values().stream().noneMatch(candidate -> sameTerminal(candidate, user))) {
                requestAuthorizationHeaders.setWebSocketHeaders(MediaWsAuthHandshakeInterceptor.headers(session));
                try {
                    int released = equipmentControlService.releaseOwnedSessions(user, "websocket_disconnected");
                    if (released > 0) {
                        log.info("WebSocket 断开已释放控制会话，会话={} 用户={} 终端={} 数量={}",
                                session.getId(), user.userId(), user.clientId(), released);
                    }
                } catch (RuntimeException exception) {
                    log.warn("WebSocket 断开释放控制会话失败，会话={} 用户={} 终端={}",
                            session.getId(), user.userId(), user.clientId(), exception);
                } finally {
                    requestAuthorizationHeaders.clearWebSocketHeaders();
                }
            }
        } finally {
            trajectoryCoordinator.removeSession(session);
            publisher.removeSession(session);
        }
    }

    private boolean sameTerminal(CurrentUser left, CurrentUser right) {
        return Objects.equals(left.userId(), right.userId())
                && Objects.equals(left.clientId(), right.clientId());
    }

    /**
     * 从 WebSocket 会话解析当前用户。
     *
     * @param session WebSocket 会话
     * @return 当前用户
     */
    private CurrentUser currentUser(WebSocketSession session) {
        return currentUserResolver.resolve(session);
    }

    private List<Map<String, Object>> authorizedRingingCalls() {
        Set<String> robotIds = managementClient.devices().stream()
                .map(device -> firstString(device, "serialNumber", "robotId"))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return intercomCallService.ringingCalls().stream()
                .filter(call -> robotIds.contains(firstString(call, "robotId")))
                .toList();
    }

    private void requireAuthorizedCall(String callId) {
        boolean authorized = authorizedRingingCalls().stream()
                .anyMatch(call -> Objects.equals(callId, firstString(call, "callId")));
        if (!authorized) {
            throw new IllegalArgumentException("来电不存在或无权操作");
        }
    }

    private String firstString(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        return null;
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
        publisher.send(session, new TextMessage(objectMapper.writeValueAsString(body)));
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
