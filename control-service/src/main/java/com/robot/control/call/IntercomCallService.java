package com.robot.control.call;

import com.robot.control.auth.CurrentUser;
import com.robot.control.client.ControlMediaServiceClient;
import com.robot.control.config.DateTimeConfig;
import com.robot.control.dto.ControlStartVideoRequest;
import com.robot.control.dto.IntercomResponse;
import com.robot.control.dto.IntercomStatus;
import com.robot.control.dto.VideoChannel;
import com.robot.control.dto.VideoQuality;
import com.robot.control.messaging.RobotMediaCommandService;
import com.robot.control.robot.dto.RobotCameraResponse;
import com.robot.control.robot.dto.RobotDeviceResponse;
import com.robot.control.robot.service.RobotRegistryService;
import com.robot.control.service.ControlVideoCommandService;
import com.robot.control.ws.MediaWebSocketPublisher;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Coordinates robot initiated calls before the existing media intercom starts. */
@Service
public class IntercomCallService {

    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int MIN_TIMEOUT_SECONDS = 5;
    private static final int MAX_TIMEOUT_SECONDS = 120;

    private final Map<String, Call> calls = new ConcurrentHashMap<>();
    private final ControlVideoCommandService videoCommandService;
    private final ControlMediaServiceClient mediaServiceClient;
    private final RobotMediaCommandService commandService;
    private final MediaWebSocketPublisher webSocketPublisher;
    private final RobotRegistryService robotRegistryService;

    public IntercomCallService(
            ControlVideoCommandService videoCommandService,
            ControlMediaServiceClient mediaServiceClient,
            RobotMediaCommandService commandService,
            MediaWebSocketPublisher webSocketPublisher,
            RobotRegistryService robotRegistryService) {
        this.videoCommandService = videoCommandService;
        this.mediaServiceClient = mediaServiceClient;
        this.commandService = commandService;
        this.webSocketPublisher = webSocketPublisher;
        this.robotRegistryService = robotRegistryService;
    }

    public synchronized void invite(IntercomCallInvite invite, String topicRobotId) {
        validateInvite(invite, topicRobotId);
        Call existing = calls.get(invite.callId());
        if (existing != null) {
            sendRobotState(existing, "duplicate invite");
            if (existing.status == IntercomCallStatus.RINGING) {
                webSocketPublisher.publish("video.intercom.call.incoming", payload(existing));
            }
            return;
        }

        boolean robotBusy;
        try {
            robotBusy = isRobotBusy(invite.robotId());
        } catch (RuntimeException ex) {
            Call failed = Call.from(invite, robotName(invite.robotId()), cameraName(invite.robotId(), invite.deviceId()), now());
            failed.status = IntercomCallStatus.FAILED;
            failed.message = "failed to check intercom occupancy";
            calls.put(failed.callId, failed);
            sendRobotState(failed, failed.message);
            publishStatus(failed);
            return;
        }

        if (robotBusy) {
            Call busy = Call.from(invite, robotName(invite.robotId()), cameraName(invite.robotId(), invite.deviceId()), now());
            busy.status = IntercomCallStatus.BUSY;
            calls.put(busy.callId, busy);
            sendRobotState(busy, "robot intercom is busy");
            publishStatus(busy);
            return;
        }

        Call call = Call.from(invite, robotName(invite.robotId()), cameraName(invite.robotId(), invite.deviceId()), now());
        calls.put(call.callId, call);
        sendRobotState(call, "control center is ringing");
        webSocketPublisher.publish("video.intercom.call.incoming", payload(call));
    }

    public synchronized void cancel(IntercomCallCancel cancel, String topicRobotId) {
        if (cancel == null || blank(cancel.callId()) || blank(topicRobotId)) {
            return;
        }
        Call call = calls.get(cancel.callId());
        if (call == null || !call.robotId.equals(topicRobotId) || call.status != IntercomCallStatus.RINGING) {
            return;
        }
        call.status = IntercomCallStatus.CANCELED;
        call.message = blank(cancel.reason()) ? "robot canceled" : cancel.reason();
        call.updatedAt = now();
        sendRobotState(call, call.message);
        publishStatus(call);
    }

    public synchronized Map<String, Object> accept(String callId, CurrentUser user) {
        requireOperator(user);
        Call call = requireRinging(callId);
        RobotDeviceResponse robot = robotRegistryService.find(call.robotId).orElse(null);
        if (robot == null || !"online".equalsIgnoreCase(robot.status())) {
            fail(call, "robot is offline");
            throw new IllegalStateException("机器人已离线");
        }

        call.status = IntercomCallStatus.ACCEPTED;
        call.acceptedBy = user.userId();
        call.acceptedClientId = user.clientId();
        call.updatedAt = now();
        try {
            ControlStartVideoRequest request = new ControlStartVideoRequest();
            request.setChannel(parseChannel(call.channel));
            request.setQuality(parseQuality(call.quality));
            request.setReuse(true);
            IntercomResponse intercom = videoCommandService.startIntercom(call.robotId, call.deviceId, request, user);
            call.sessionId = intercom.sessionId();
            call.message = "operator accepted";
            sendRobotState(call, call.message);
            publishStatus(call);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("call", payload(call));
            result.put("intercom", intercom);
            return result;
        } catch (RuntimeException ex) {
            fail(call, ex.getMessage());
            throw ex;
        }
    }

    public synchronized Map<String, Object> reject(String callId, CurrentUser user) {
        requireOperator(user);
        Call call = requireRinging(callId);
        call.status = IntercomCallStatus.REJECTED;
        call.acceptedBy = user.userId();
        call.acceptedClientId = user.clientId();
        call.message = "operator rejected";
        call.updatedAt = now();
        sendRobotState(call, call.message);
        publishStatus(call);
        return payload(call);
    }

    public synchronized List<Map<String, Object>> ringingCalls() {
        OffsetDateTime current = now();
        List<Map<String, Object>> result = new ArrayList<>();
        calls.values().stream()
                .filter(call -> call.status == IntercomCallStatus.RINGING && call.expiresAt.isAfter(current))
                .sorted((left, right) -> left.createdAt.compareTo(right.createdAt))
                .forEach(call -> result.add(payload(call)));
        return result;
    }

    public synchronized void handleIntercomStatus(String sessionId, String status, String message) {
        if (blank(sessionId) || blank(status)) {
            return;
        }
        Call call = calls.values().stream()
                .filter(item -> sessionId.equals(item.sessionId) && item.status == IntercomCallStatus.ACCEPTED)
                .findFirst()
                .orElse(null);
        if (call == null) {
            return;
        }
        if ("stopped".equalsIgnoreCase(status)) {
            end(call, blank(message) ? "intercom stopped" : message);
        } else if ("failed".equalsIgnoreCase(status)) {
            fail(call, blank(message) ? "intercom failed" : message);
        }
    }

    public synchronized void endBySession(String sessionId) {
        handleIntercomStatus(sessionId, "stopped", "operator ended");
    }

    @Scheduled(fixedDelayString = "${control.intercom-call.sweep-delay-ms:1000}")
    public synchronized void sweepTimeouts() {
        OffsetDateTime current = now();
        calls.values().stream()
                .filter(call -> call.status == IntercomCallStatus.RINGING && !call.expiresAt.isAfter(current))
                .forEach(call -> {
                    call.status = IntercomCallStatus.TIMEOUT;
                    call.message = "call timeout";
                    call.updatedAt = current;
                    sendRobotState(call, call.message);
                    publishStatus(call);
                });
        calls.values().removeIf(call -> call.status != IntercomCallStatus.RINGING
                && call.status != IntercomCallStatus.ACCEPTED
                && call.updatedAt.isBefore(current.minusHours(1)));
    }

    private boolean isRobotBusy(String robotId) {
        boolean callBusy = calls.values().stream().anyMatch(call -> call.robotId.equals(robotId)
                && (call.status == IntercomCallStatus.RINGING || call.status == IntercomCallStatus.ACCEPTED));
        if (callBusy) {
            return true;
        }
        return mediaServiceClient.active().stream().anyMatch(session -> robotId.equals(session.robotId())
                && session.intercomStatus() != null
                && session.intercomStatus() != IntercomStatus.IDLE
                && session.intercomStatus() != IntercomStatus.FAILED);
    }

    private Call requireRinging(String callId) {
        Call call = calls.get(callId);
        if (call == null) {
            throw new IllegalArgumentException("来电不存在");
        }
        if (call.status != IntercomCallStatus.RINGING || !call.expiresAt.isAfter(now())) {
            throw new IllegalStateException("来电已被处理或已超时");
        }
        return call;
    }

    private void requireOperator(CurrentUser user) {
        if (user == null || !user.hasRole("MEDIA_OPERATOR")) {
            throw new SecurityException("当前用户没有对讲接听权限");
        }
    }

    private void fail(Call call, String message) {
        call.status = IntercomCallStatus.FAILED;
        call.message = blank(message) ? "call failed" : message;
        call.updatedAt = now();
        sendRobotState(call, call.message);
        publishStatus(call);
    }

    private void end(Call call, String message) {
        call.status = IntercomCallStatus.ENDED;
        call.message = message;
        call.updatedAt = now();
        sendRobotState(call, message);
        publishStatus(call);
    }

    private void sendRobotState(Call call, String message) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("callId", call.callId);
        state.put("robotId", call.robotId);
        state.put("status", call.status.name().toLowerCase());
        if (call.sessionId != null) {
            state.put("sessionId", call.sessionId);
        }
        state.put("message", message);
        state.put("timestamp", DateTimeConfig.format(now()));
        commandService.sendIntercomCallState(call.robotId, state);
    }

    private void publishStatus(Call call) {
        webSocketPublisher.publish("video.intercom.call.status", payload(call));
    }

    private Map<String, Object> payload(Call call) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("callId", call.callId);
        data.put("robotId", call.robotId);
        data.put("robotName", call.robotName);
        data.put("deviceId", call.deviceId);
        data.put("cameraName", call.cameraName);
        data.put("channel", call.channel);
        data.put("quality", call.quality);
        data.put("reason", call.reason);
        data.put("status", call.status.name());
        data.put("expiresAt", DateTimeConfig.format(call.expiresAt));
        data.put("expiresAtEpochMillis", call.expiresAt.toInstant().toEpochMilli());
        long remainingMillis = Duration.between(now(), call.expiresAt).toMillis();
        data.put("remainingSeconds", Math.max(0L, (remainingMillis + 999L) / 1000L));
        data.put("sessionId", call.sessionId);
        data.put("acceptedBy", call.acceptedBy);
        data.put("acceptedClientId", call.acceptedClientId);
        data.put("message", call.message);
        return data;
    }

    private String robotName(String robotId) {
        return robotRegistryService.find(robotId).map(RobotDeviceResponse::name).orElse(robotId);
    }

    private String cameraName(String robotId, String deviceId) {
        return robotRegistryService.find(robotId)
                .flatMap(robot -> robot.cameras().stream()
                        .filter(camera -> deviceId.equals(camera.deviceId()) || deviceId.equals(camera.cameraId()))
                        .findFirst())
                .map(RobotCameraResponse::name)
                .orElse(deviceId);
    }

    private void validateInvite(IntercomCallInvite invite, String topicRobotId) {
        if (invite == null || blank(invite.callId()) || blank(invite.robotId()) || blank(invite.deviceId())) {
            throw new IllegalArgumentException("call invite 缺少 callId、robotId 或 deviceId");
        }
        if (!invite.robotId().equals(topicRobotId)) {
            throw new IllegalArgumentException("topic robotId 与 payload robotId 不一致");
        }
    }

    private static VideoChannel parseChannel(String value) {
        try {
            return VideoChannel.valueOf(blank(value) ? "visible" : value);
        } catch (IllegalArgumentException ex) {
            return VideoChannel.visible;
        }
    }

    private static VideoQuality parseQuality(String value) {
        try {
            return VideoQuality.valueOf(blank(value) ? "sub" : value);
        } catch (IllegalArgumentException ex) {
            return VideoQuality.sub;
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
        private String robotId;
        private String robotName;
        private String deviceId;
        private String cameraName;
        private String channel;
        private String quality;
        private String reason;
        private IntercomCallStatus status;
        private OffsetDateTime createdAt;
        private OffsetDateTime updatedAt;
        private OffsetDateTime expiresAt;
        private String sessionId;
        private String acceptedBy;
        private String acceptedClientId;
        private String message;

        private static Call from(IntercomCallInvite invite, String robotName, String cameraName, OffsetDateTime current) {
            Call call = new Call();
            call.callId = invite.callId();
            call.robotId = invite.robotId();
            call.robotName = robotName;
            call.deviceId = invite.deviceId();
            call.cameraName = cameraName;
            call.channel = blank(invite.channel()) ? "visible" : invite.channel();
            call.quality = blank(invite.quality()) ? "sub" : invite.quality();
            call.reason = blank(invite.reason()) ? "机器人请求人工对讲" : invite.reason();
            call.status = IntercomCallStatus.RINGING;
            call.createdAt = current;
            call.updatedAt = current;
            int requestedTimeout = invite.timeoutSeconds() == null ? DEFAULT_TIMEOUT_SECONDS : invite.timeoutSeconds();
            int timeout = Math.max(MIN_TIMEOUT_SECONDS, Math.min(MAX_TIMEOUT_SECONDS, requestedTimeout));
            call.expiresAt = current.plusSeconds(timeout);
            return call;
        }
    }
}
