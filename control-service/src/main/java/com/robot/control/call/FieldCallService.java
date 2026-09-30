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
 * 现场应用 → 指挥中心视频呼叫状态机（内存单实例，对标 {@link IntercomCallService}）。
 */
@Service
public class FieldCallService {

    private static final Logger log = LoggerFactory.getLogger(FieldCallService.class);
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    /** App 信令短暂断线（JWT 续期重连 / 弱网）时，保留通话的宽限秒数。 */
    private static final int APP_DISCONNECT_GRACE_SECONDS = 45;

    /**
     * 按呼叫 ID 保存本实例现场呼叫状态；终态记录由超时扫描回收。
     */
    private final Map<String, Call> calls = new ConcurrentHashMap<>();
    /**
     * 现场用户 ID 到当前 App 信令连接的映射，重连后替换旧连接。
     */
    private final Map<String, WebSocketSession> appSessions = new ConcurrentHashMap<>();
    private final ControlMediaServiceClient mediaServiceClient;
    private final MediaWebSocketPublisher webSocketPublisher;
    private final ObjectMapper objectMapper;

    /**
     * 初始化 FieldCallService，保存所需依赖及初始运行状态。
     *
     * @param mediaServiceClient 媒体服务 客户端
     * @param webSocketPublisher 向已连接客户端投递业务事件的组件
     * @param objectMapper JSON 编解码器
     */
    public FieldCallService(
            ControlMediaServiceClient mediaServiceClient,
            MediaWebSocketPublisher webSocketPublisher,
            ObjectMapper objectMapper) {
        this.mediaServiceClient = mediaServiceClient;
        this.webSocketPublisher = webSocketPublisher;
        this.objectMapper = objectMapper;
    }

    /**
     * 将现场用户与当前 App 连接绑定，供呼叫通知定向投递。
     *
     * @param userId 用户 ID
     * @param session WebSocket 会话
     */
    public synchronized void bindAppSession(String userId, WebSocketSession session) {
        if (userId == null || session == null) {
            return;
        }
        appSessions.put(userId, session);
        for (Call call : calls.values()) {
            if (!userId.equals(call.appUserId)) {
                continue;
            }
            if (call.status == FieldCallStatus.RINGING || call.status == FieldCallStatus.ACCEPTED) {
                call.appSession = session;
                call.appDisconnectAt = null;
            }
        }
    }

    /**
     * 移除现场应用 连接绑定，避免通知继续发送到失效会话。
     *
     * @param session WebSocket 会话
     */
    public synchronized void unbindAppSession(WebSocketSession session) {
        if (session == null) {
            return;
        }
        appSessions.entrySet().removeIf(entry -> entry.getValue() == session);
        for (Call call : List.copyOf(calls.values())) {
            if (call.appSession == session) {
                call.appSession = null;
                if (call.status == FieldCallStatus.RINGING || call.status == FieldCallStatus.ACCEPTED) {
                    // 宽限内允许 App 用新 JWT 重连，避免短暂断线直接挂断。
                    call.appDisconnectAt = now();
                    log.info("现场应用 信令断开，进入重连宽限 呼叫标识={} 宽限秒数={}",
                            call.callId, APP_DISCONNECT_GRACE_SECONDS);
                }
            }
        }
    }

    /**
     * 校验目标与占用后创建现场呼叫邀请，设置应答期限并通知被叫端。
     *
     * @param user 当前用户
     * @param displayName 参与者对外显示名称
     * @param appSession 现场应用 当前 WebSocket 连接
     * @return 呼叫 ID、当前状态及邀请信息
     */
    public synchronized Map<String, Object> invite(
            CurrentUser user,
            String displayName,
            WebSocketSession appSession) {
        requireFieldOperator(user);
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

    /**
     * 校验媒体操作员角色及振铃状态后接听邀请，创建媒体房间并登记接听用户和客户端。
     * @param callId 呼叫 ID
     * @param operator 发起当前操作的可信用户及角色上下文
     * @return 接听后的通话与媒体接入信息
     */
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

    /**
     * 拒绝仍在等待应答的现场呼叫并通知发起方。
     *
     * @param callId 呼叫 ID
     * @param operator 发起当前操作的可信用户及角色上下文
     * @return 拒绝后的呼叫状态
     */
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

    /**
     * 由发起方撤销尚未接听的现场呼叫。
     *
     * @param callId 呼叫 ID
     * @param user 当前用户
     */
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

    /**
     * 结束当前振铃中或已接通的现场呼叫并释放双方占用。
     *
     * @param callId 呼叫 ID
     * @param user 当前用户
     * @param reason 结束通话的原因说明，空值使用既有消息值 hangup；中心端挂断时随结束事件通知现场端
     */
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

    /**
     * 查询本实例全部未过期的振铃邀请；本方法不按当前用户过滤。
     * @return 本实例当前有效的待接听呼叫列表
     */
    public synchronized List<Map<String, Object>> ringingCalls() {
        OffsetDateTime current = now();
        List<Map<String, Object>> result = new ArrayList<>();
        calls.values().stream()
                .filter(call -> call.status == FieldCallStatus.RINGING && call.expiresAt.isAfter(current))
                .sorted((a, b) -> a.createdAt.compareTo(b.createdAt))
                .forEach(call -> result.add(centerPayload(call)));
        return result;
    }

    /**
     * 定期收口超过应答期限的邀请并通知参与方。
     */
    @Scheduled(fixedDelayString = "${control.field-call.sweep-delay-ms:1000}")
    public synchronized void sweepTimeouts() {
        OffsetDateTime current = now();
        for (Call call : List.copyOf(calls.values())) {
            if (call.status == FieldCallStatus.RINGING && !call.expiresAt.isAfter(current)) {
                call.status = FieldCallStatus.TIMEOUT;
                call.message = "无人接听";
                call.updatedAt = current;
                call.appDisconnectAt = null;
                sendToApp(call, Map.of(
                        "type", "field.call.timeout",
                        "callId", call.callId,
                        "message", "无人接听"));
                publishStatus(call);
                continue;
            }
            if (call.appDisconnectAt != null
                    && (call.status == FieldCallStatus.RINGING || call.status == FieldCallStatus.ACCEPTED)
                    && call.appDisconnectAt.plusSeconds(APP_DISCONNECT_GRACE_SECONDS).isBefore(current)
                    && (call.appSession == null || !call.appSession.isOpen())) {
                end(call, "mobile-left");
                call.appDisconnectAt = null;
                sendToApp(call, Map.of(
                        "type", "field.call.ended",
                        "callId", call.callId,
                        "reason", "mobile-left"));
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

    private void requireFieldOperator(CurrentUser user) {
        if (user == null || blank(user.userId())) {
            throw new IllegalArgumentException("缺少用户身份");
        }
        if (!user.hasRole("FIELD_OPERATOR")) {
            throw new SecurityException("当前用户没有现场呼叫发起权限");
        }
    }

    private void fail(Call call, String message) {
        call.status = FieldCallStatus.FAILED;
        call.message = blank(message) ? "call failed" : message;
        call.updatedAt = now();
        call.appDisconnectAt = null;
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
        call.appDisconnectAt = null;
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
        data.put("robotName", call.displayName);
        data.put("robotId", "app-" + call.appUserId);
        data.put("deviceId", "phone-camera");
        data.put("cameraId", "phone-camera");
        data.put("cameraName", "手机摄像头");
        data.put("reason", "邀请你进行视频通话");
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
            log.warn("向现场应用 发送信令失败 呼叫标识={}", call.callId, ex);
        }
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    /** 保存现场主动呼叫的参与者、媒体会话及呼叫生命周期状态。 */
    private static final class Call {
        /**
         * 现场呼叫唯一标识。
         */
        private String callId;
        /**
         * 发起现场呼叫的用户 ID。
         */
        private String appUserId;
        /**
         * 发起用户所属组织 ID。
         */
        private String orgId;
        /**
         * 向指挥中心展示的现场用户名称。
         */
        private String displayName;
        /**
         * 当前现场呼叫生命周期状态。
         */
        private FieldCallStatus status;
        /**
         * 服务端创建呼叫的时间。
         */
        private OffsetDateTime createdAt;
        /**
         * 服务端最近更新呼叫状态的时间。
         */
        private OffsetDateTime updatedAt;
        /**
         * 振铃邀请的应答截止时间。
         */
        private OffsetDateTime expiresAt;
        /**
         * 接听或拒绝本次呼叫的中心用户 ID。
         */
        private String acceptedBy;
        /**
         * 处理本次呼叫的中心客户端 ID。
         */
        private String acceptedClientId;
        /**
         * 接听后创建的 LiveKit 房间名。
         */
        private String roomName;
        /**
         * 当前状态原因，随呼叫事件传递。
         */
        private String message;
        /**
         * 通知现场端使用的当前信令连接，断线后可由重连替换。
         */
        private WebSocketSession appSession;
        /** 非空表示 App 信令已断，等待宽限内重连。 */
        private OffsetDateTime appDisconnectAt;
    }
}
