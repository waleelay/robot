package com.robot.control.call;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.control.auth.CurrentUser;
import com.robot.control.client.ControlMediaServiceClient;
import com.robot.control.config.DateTimeConfig;
import com.robot.control.ws.MediaWebSocketPublisher;
import com.robot.media.common.video.CreateFieldCallRequest;
import com.robot.media.common.video.FieldCallResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * 现场 App → 指挥中心视频呼叫状态机（内存单实例，对标 {@link IntercomCallService}）。
 */
@Service
public class FieldCallService {

    private static final Logger log = LoggerFactory.getLogger(FieldCallService.class);
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    private final Map<String, Call> calls = new ConcurrentHashMap<>();
    private final Map<String, WebSocketSession> appSessions = new ConcurrentHashMap<>();
    private final ControlMediaServiceClient mediaServiceClient;
    private final MediaWebSocketPublisher webSocketPublisher;
    private final ObjectMapper objectMapper;

    public FieldCallService(
            ControlMediaServiceClient mediaServiceClient,
            MediaWebSocketPublisher webSocketPublisher,
            ObjectMapper objectMapper) {
        this.mediaServiceClient = mediaServiceClient;
        this.webSocketPublisher = webSocketPublisher;
        this.objectMapper = objectMapper;
    }

    public synchronized void bindAppSession(String userId, WebSocketSession session) {
        if (userId == null || session == null) {
            return;
        }
        appSessions.put(userId, session);
        for (Call call : calls.values()) {
            if (call.status == FieldCallStatus.RINGING && userId.equals(call.appUserId)) {
                call.appSession = session;
            }
        }
    }

    public synchronized void unbindAppSession(WebSocketSession session) {
        if (session == null) {
            return;
        }
        appSessions.entrySet().removeIf(entry -> entry.getValue() == session);
        for (Call call : List.copyOf(calls.values())) {
            if (call.appSession == session) {
                if (call.status == FieldCallStatus.RINGING || call.status == FieldCallStatus.ACCEPTED) {
                    end(call, "mobile-left");
                    sendToApp(call, Map.of(
                            "type", "field.call.ended",
                            "callId", call.callId,
                            "reason", "mobile-left"));
                }
                call.appSession = null;
            }
        }
    }

    public synchronized Map<String, Object> invite(
            CurrentUser user,
            String displayName,
            WebSocketSession appSession) {
        if (user == null || blank(user.userId())) {
            throw new IllegalArgumentException("缺少用户身份");
        }
        Call active = findActiveByUser(user.userId());
        if (active != null) {
            if (active.status == FieldCallStatus.RINGING) {
                end(active, "replaced");
                sendToApp(active, Map.of(
                        "type", "field.call.ended",
                        "callId", active.callId,
                        "reason", "replaced"));
            } else {
                return Map.of(
                        "type", "field.call.busy",
                        "callId", active.callId,
                        "message", "已有进行中的现场呼叫");
            }
        }

        Call call = new Call();
        call.callId = UUID.randomUUID().toString();
        call.appUserId = user.userId();
        call.orgId = user.orgId();
        call.displayName = blank(displayName) ? "现场操作员" : displayName.trim();
        call.status = FieldCallStatus.RINGING;
        call.createdAt = now();
        call.updatedAt = call.createdAt;
        call.expiresAt = call.createdAt.plusSeconds(DEFAULT_TIMEOUT_SECONDS);
        call.appSession = appSession;
        calls.put(call.callId, call);
        appSessions.put(user.userId(), appSession);

        webSocketPublisher.publish("video.field.call.incoming", centerPayload(call));
        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("type", "field.call.invite.ok");
        ok.put("callId", call.callId);
        ok.put("status", FieldCallStatus.RINGING.name());
        ok.put("expiresAt", DateTimeConfig.format(call.expiresAt));
        return ok;
    }

    public synchronized Map<String, Object> accept(String callId, CurrentUser operator) {
        requireOperator(operator);
        Call call = requireRinging(callId);
        try {
            FieldCallResponse media = mediaServiceClient.createFieldCall(
                    new CreateFieldCallRequest(
                            call.callId,
                            call.orgId,
                            call.appUserId,
                            operator.userId(),
                            operator.clientId()),
                    operator);
            call.status = FieldCallStatus.ACCEPTED;
            call.acceptedBy = operator.userId();
            call.acceptedClientId = operator.clientId();
            call.roomName = media.roomName();
            call.livekitUrl = media.livekitUrl();
            call.updatedAt = now();
            call.message = "operator accepted";

            Map<String, Object> appAccepted = new LinkedHashMap<>();
            appAccepted.put("type", "field.call.accepted");
            appAccepted.put("callId", call.callId);
            appAccepted.put("livekitUrl", media.livekitUrl());
            appAccepted.put("roomName", media.roomName());
            appAccepted.put("token", media.appToken());
            sendToApp(call, appAccepted);

            publishStatus(call);

            Map<String, Object> session = new LinkedHashMap<>();
            session.put("livekitUrl", media.livekitUrl());
            session.put("roomName", media.roomName());
            session.put("token", media.centerToken());
            session.put("expiresAt", DateTimeConfig.format(media.expiresAt()));

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("call", centerPayload(call));
            result.put("session", session);
            return result;
        } catch (RuntimeException ex) {
            fail(call, ex.getMessage());
            throw ex;
        }
    }

    public synchronized Map<String, Object> reject(String callId, CurrentUser operator) {
        requireOperator(operator);
        Call call = requireRinging(callId);
        call.status = FieldCallStatus.REJECTED;
        call.acceptedBy = operator.userId();
        call.acceptedClientId = operator.clientId();
        call.message = "operator rejected";
        call.updatedAt = now();
        sendToApp(call, Map.of("type", "field.call.rejected", "callId", call.callId));
        publishStatus(call);
        return centerPayload(call);
    }

    public synchronized void cancel(String callId, CurrentUser user) {
        Call call = calls.get(callId);
        if (call == null || call.status != FieldCallStatus.RINGING) {
            return;
        }
        if (user == null || !user.userId().equals(call.appUserId)) {
            throw new SecurityException("只能取消自己发起的呼叫");
        }
        call.status = FieldCallStatus.CANCELED;
        call.message = "app canceled";
        call.updatedAt = now();
        publishStatus(call);
    }

    public synchronized void hangup(String callId, CurrentUser user, String reason) {
        Call call = calls.get(callId);
        if (call == null) {
            return;
        }
        if (call.status != FieldCallStatus.RINGING && call.status != FieldCallStatus.ACCEPTED) {
            return;
        }
        boolean isApp = user != null && user.userId().equals(call.appUserId);
        boolean isCenter = user != null && (
                user.userId().equals(call.acceptedBy) || user.hasRole("MEDIA_OPERATOR"));
        if (!isApp && !isCenter) {
            throw new SecurityException("无权结束该呼叫");
        }
        end(call, blank(reason) ? "hangup" : reason);
        Map<String, Object> ended = Map.of(
                "type", "field.call.ended",
                "callId", call.callId,
                "reason", call.message);
        if (isCenter) {
            sendToApp(call, ended);
        }
    }

    public synchronized List<Map<String, Object>> ringingCalls() {
        OffsetDateTime current = now();
        List<Map<String, Object>> result = new ArrayList<>();
        calls.values().stream()
                .filter(call -> call.status == FieldCallStatus.RINGING && call.expiresAt.isAfter(current))
                .sorted((a, b) -> a.createdAt.compareTo(b.createdAt))
                .forEach(call -> result.add(centerPayload(call)));
        return result;
    }

    @Scheduled(fixedDelayString = "${control.field-call.sweep-delay-ms:1000}")
    public synchronized void sweepTimeouts() {
        OffsetDateTime current = now();
        for (Call call : List.copyOf(calls.values())) {
            if (call.status == FieldCallStatus.RINGING && !call.expiresAt.isAfter(current)) {
                call.status = FieldCallStatus.TIMEOUT;
                call.message = "call timeout";
                call.updatedAt = current;
                sendToApp(call, Map.of(
                        "type", "field.call.timeout",
                        "callId", call.callId));
                publishStatus(call);
            }
        }
        calls.values().removeIf(call -> call.status != FieldCallStatus.RINGING
                && call.status != FieldCallStatus.ACCEPTED
                && call.updatedAt.isBefore(current.minusHours(1)));
    }

    private Call findActiveByUser(String userId) {
        return calls.values().stream()
                .filter(call -> userId.equals(call.appUserId)
                        && (call.status == FieldCallStatus.RINGING || call.status == FieldCallStatus.ACCEPTED))
                .findFirst()
                .orElse(null);
    }

    private Call requireRinging(String callId) {
        Call call = calls.get(callId);
        if (call == null) {
            throw new IllegalArgumentException("现场来电不存在");
        }
        if (call.status != FieldCallStatus.RINGING || !call.expiresAt.isAfter(now())) {
            throw new IllegalStateException("现场来电已被处理或已超时");
        }
        return call;
    }

    private void requireOperator(CurrentUser user) {
        if (user == null || !user.hasRole("MEDIA_OPERATOR")) {
            throw new SecurityException("当前用户没有现场呼叫接听权限");
        }
    }

    private void fail(Call call, String message) {
        call.status = FieldCallStatus.FAILED;
        call.message = blank(message) ? "call failed" : message;
        call.updatedAt = now();
        sendToApp(call, Map.of(
                "type", "error",
                "callId", call.callId,
                "message", call.message));
        publishStatus(call);
    }

    private void end(Call call, String message) {
        call.status = FieldCallStatus.ENDED;
        call.message = message;
        call.updatedAt = now();
        publishStatus(call);
    }

    private void publishStatus(Call call) {
        webSocketPublisher.publish("video.field.call.status", centerPayload(call));
    }

    private Map<String, Object> centerPayload(Call call) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("callId", call.callId);
        data.put("source", "mobile-app");
        data.put("displayName", call.displayName);
        data.put("robotName", "现场 App · " + call.displayName);
        data.put("robotId", "app-" + call.appUserId);
        data.put("deviceId", "phone-camera");
        data.put("cameraId", "phone-camera");
        data.put("cameraName", "手机摄像头");
        data.put("reason", "现场 App 邀请你进行视频通话");
        data.put("status", call.status.name());
        data.put("expiresAt", DateTimeConfig.format(call.expiresAt));
        data.put("expiresAtEpochMillis", call.expiresAt.toInstant().toEpochMilli());
        long remainingMillis = Duration.between(now(), call.expiresAt).toMillis();
        data.put("remainingSeconds", Math.max(0L, (remainingMillis + 999L) / 1000L));
        data.put("roomName", call.roomName);
        data.put("acceptedBy", call.acceptedBy);
        data.put("acceptedClientId", call.acceptedClientId);
        data.put("message", call.message);
        data.put("appUserId", call.appUserId);
        return data;
    }

    private void sendToApp(Call call, Map<String, Object> payload) {
        WebSocketSession session = call.appSession;
        if (session == null) {
            session = appSessions.get(call.appUserId);
            call.appSession = session;
        }
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(payload);
            synchronized (session) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (IOException | IllegalStateException ex) {
            log.warn("向现场 App 发送信令失败 callId={}", call.callId, ex);
        }
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static final class Call {
        private String callId;
        private String appUserId;
        private String orgId;
        private String displayName;
        private FieldCallStatus status;
        private OffsetDateTime createdAt;
        private OffsetDateTime updatedAt;
        private OffsetDateTime expiresAt;
        private String acceptedBy;
        private String acceptedClientId;
        private String roomName;
        private String livekitUrl;
        private String message;
        private WebSocketSession appSession;
    }
}
