package com.robot.control.ws;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.control.auth.CurrentUser;
import com.robot.control.auth.CurrentUserResolver;
import com.robot.control.auth.RequestAuthorizationHeaders;
import com.robot.control.call.FieldCallService;
import java.net.URI;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 现场 App WebSocket 信令（/ws/field-call）。
 */
@Component
public class FieldCallWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(FieldCallWebSocketHandler.class);

    private final FieldCallService fieldCallService;
    private final ObjectMapper objectMapper;
    private final CurrentUserResolver currentUserResolver;
    private final RequestAuthorizationHeaders requestAuthorizationHeaders;
    private final MediaWebSocketPublisher publisher;

    public FieldCallWebSocketHandler(
            FieldCallService fieldCallService,
            ObjectMapper objectMapper,
            CurrentUserResolver currentUserResolver,
            RequestAuthorizationHeaders requestAuthorizationHeaders,
            MediaWebSocketPublisher publisher) {
        this.fieldCallService = fieldCallService;
        this.objectMapper = objectMapper;
        this.currentUserResolver = currentUserResolver;
        this.requestAuthorizationHeaders = requestAuthorizationHeaders;
        this.publisher = publisher;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        requestAuthorizationHeaders.setWebSocketHeaders(MediaWsAuthHandshakeInterceptor.headers(session));
        try {
            CurrentUser user = currentUserResolver.resolve(session);
            fieldCallService.bindAppSession(user.userId(), session);
            sendRaw(session, Map.of(
                    "type", "field.call.hello.ok",
                    "userId", user.userId(),
                    "orgId", user.orgId()));
            // 新中心端不走此通道；中心仍用 /ws/control。此处仅确认 App 已连上。
        } catch (RuntimeException ex) {
            log.warn("现场呼叫握手失败 session={}", session.getId(), ex);
            try {
                session.close(CloseStatus.NOT_ACCEPTABLE.withReason(ex.getMessage()));
            } catch (Exception ignored) {
                // ignore
            }
        } finally {
            requestAuthorizationHeaders.clearWebSocketHeaders();
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Map<String, Object> incoming = objectMapper.readValue(message.getPayload(), new TypeReference<>() {});
        String type = stringValue(incoming.get("type"), "");
        requestAuthorizationHeaders.setWebSocketHeaders(MediaWsAuthHandshakeInterceptor.headers(session));
        try {
            CurrentUser user = currentUserResolver.resolve(session);
            switch (type) {
                case "field.call.invite" -> {
                    String displayName = stringValue(incoming.get("displayName"), query(session, "displayName"));
                    Map<String, Object> result = fieldCallService.invite(user, displayName, session);
                    sendRaw(session, result);
                }
                case "field.call.cancel" -> {
                    fieldCallService.cancel(stringValue(incoming.get("callId"), ""), user);
                    sendRaw(session, Map.of(
                            "type", "field.call.cancel.ok",
                            "callId", stringValue(incoming.get("callId"), "")));
                }
                case "field.call.hangup" -> {
                    fieldCallService.hangup(
                            stringValue(incoming.get("callId"), ""),
                            user,
                            stringValue(incoming.get("reason"), "hangup"));
                    sendRaw(session, Map.of(
                            "type", "field.call.ended",
                            "callId", stringValue(incoming.get("callId"), ""),
                            "reason", "hangup"));
                }
                default -> sendRaw(session, Map.of(
                        "type", "error",
                        "message", "unknown type " + type));
            }
        } catch (Exception ex) {
            log.warn("现场呼叫信令处理失败 type={}", type, ex);
            sendRaw(session, Map.of(
                    "type", "error",
                    "message", ex.getMessage() == null ? "error" : ex.getMessage()));
        } finally {
            requestAuthorizationHeaders.clearWebSocketHeaders();
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        fieldCallService.unbindAppSession(session);
    }

    private void sendRaw(WebSocketSession session, Map<String, Object> payload) throws Exception {
        String json = objectMapper.writeValueAsString(payload);
        publisher.send(session, new TextMessage(json));
    }

    private static String stringValue(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue == null ? "" : defaultValue;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? (defaultValue == null ? "" : defaultValue) : text;
    }

    private static String query(WebSocketSession session, String name) {
        URI uri = session.getUri();
        if (uri == null) {
            return "";
        }
        String value = UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst(name);
        return value == null ? "" : value;
    }
}
