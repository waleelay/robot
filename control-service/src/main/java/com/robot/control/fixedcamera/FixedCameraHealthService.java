package com.robot.control.fixedcamera;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.control.ws.MediaWebSocketPublisher;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** 保存固定摄像头 Gateway 与 RTSP 最近健康状态。 */
@Service
public class FixedCameraHealthService {

    private static final Logger log = LoggerFactory.getLogger(FixedCameraHealthService.class);
    private static final String VERSION = "1.0";

    private final ObjectMapper objectMapper;
    private final MediaWebSocketPublisher webSocketPublisher;
    private final Map<String, GatewayState> gateways = new ConcurrentHashMap<>();
    private final Map<String, CameraState> cameras = new ConcurrentHashMap<>();

    @Value("${control.fixed-camera-health.gateway-timeout-seconds:30}")
    private long gatewayTimeoutSeconds = 30;

    @Value("${control.fixed-camera-health.camera-max-age-seconds:120}")
    private long cameraMaxAgeSeconds = 120;

    public FixedCameraHealthService(ObjectMapper objectMapper, MediaWebSocketPublisher webSocketPublisher) {
        this.objectMapper = objectMapper;
        this.webSocketPublisher = webSocketPublisher;
    }

    public void handleGatewayStatus(String topic, byte[] payload) {
        String[] parts = topic == null ? new String[0] : topic.split("/");
        if (parts.length != 4 || !"gateway".equals(parts[0]) || !"fixed-camera".equals(parts[1])
                || !"status".equals(parts[3])) {
            log.warn("已拒绝格式错误的固定摄像头网关状态主题，主题={}", topic);
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            String topicGatewayId = parts[2];
            String payloadGatewayId = text(root, "gatewayId");
            if (!topicGatewayId.equals(payloadGatewayId)) {
                log.warn("已拒绝网关 ID 与主题不一致的固定摄像头状态，主题网关={} 载荷网关={}",
                        topicGatewayId, payloadGatewayId);
                return;
            }
            String status = enumValue(root, "status", "ONLINE", "OFFLINE", "UNKNOWN");
            if (status == null) {
                log.warn("已拒绝状态值无效的固定摄像头网关消息，网关={}", topicGatewayId);
                return;
            }
            Instant now = Instant.now();
            GatewayState incoming = new GatewayState(
                    topicGatewayId, status, longValue(root, "sequence"), instant(root, "reportedAt"),
                    now, text(root, "reasonCode"));
            gateways.compute(topicGatewayId, (ignored, previous) -> {
                if (previous != null && stale(incoming.sequence(), incoming.reportedAt(), previous.sequence(), previous.reportedAt())
                        && !"OFFLINE".equals(status)) {
                    return previous;
                }
                if (previous == null || !previous.status().equals(incoming.status())) {
                    publishGatewayChange(incoming);
                }
                return incoming;
            });
        } catch (Exception exception) {
            log.warn("解析固定摄像头网关状态失败，主题={} 载荷字节数={}", topic,
                    payload == null ? 0 : payload.length, exception);
        }
    }

    public void handleCameraStatus(String topic, byte[] payload) {
        String[] parts = topic == null ? new String[0] : topic.split("/");
        if (parts.length != 6 || !"gateway".equals(parts[0]) || !"fixed-camera".equals(parts[1])
                || !"camera".equals(parts[3]) || !"status".equals(parts[5])) {
            log.warn("已拒绝格式错误的固定摄像头健康主题，主题={}", topic);
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            String topicGatewayId = parts[2];
            String topicCameraId = parts[4];
            if (!topicGatewayId.equals(text(root, "gatewayId")) || !topicCameraId.equals(text(root, "cameraId"))) {
                log.warn("已拒绝摄像头或网关 ID 与主题不一致的健康消息，主题={}", topic);
                return;
            }
            String health = enumValue(root, "health", "AVAILABLE", "UNAVAILABLE", "UNKNOWN");
            if (health == null) {
                log.warn("已拒绝状态值无效的固定摄像头健康消息，摄像头={}", topicCameraId);
                return;
            }
            Instant now = Instant.now();
            CameraState incoming = new CameraState(
                    topicGatewayId, topicCameraId, health, longValue(root, "sequence"),
                    instant(root, "checkedAt"), now, text(root, "reasonCode"));
            cameras.compute(topicCameraId, (ignored, previous) -> {
                if (previous != null && stale(incoming.sequence(), incoming.checkedAt(), previous.sequence(), previous.checkedAt())) {
                    return previous;
                }
                if (previous == null || !previous.health().equals(incoming.health())
                        || !previous.reasonCode().equals(incoming.reasonCode())) {
                    publishCameraChange(incoming);
                }
                return incoming;
            });
        } catch (Exception exception) {
            log.warn("解析固定摄像头健康状态失败，主题={} 载荷字节数={}", topic,
                    payload == null ? 0 : payload.length, exception);
        }
    }

    public Map<String, Object> authorizedSnapshot(List<Map<String, Object>> authorizedCameras, String defaultGatewayId) {
        List<Map<String, Object>> records = new ArrayList<>();
        for (Map<String, Object> camera : authorizedCameras == null ? List.<Map<String, Object>>of() : authorizedCameras) {
            String cameraId = firstString(camera, "cameraId", "id");
            if (cameraId == null) {
                continue;
            }
            String gatewayId = firstString(camera, "gatewayId");
            if (gatewayId == null) {
                gatewayId = defaultGatewayId;
            }
            records.add(cameraView(cameraId, gatewayId));
        }
        return Map.of("version", VERSION, "records", records, "serverTime", Instant.now().toString());
    }

    @Scheduled(fixedDelayString = "${control.fixed-camera-health.sweep-delay-ms:1000}")
    void expireStaleStates() {
        expireStaleStates(Instant.now());
    }

    void expireStaleStates(Instant now) {
        Duration gatewayTimeout = Duration.ofSeconds(Math.max(1, gatewayTimeoutSeconds));
        gateways.replaceAll((gatewayId, state) -> {
            if (!"ONLINE".equals(state.status()) || state.receivedAt().plus(gatewayTimeout).isAfter(now)) {
                return state;
            }
            GatewayState expired = new GatewayState(gatewayId, "OFFLINE", state.sequence(), state.reportedAt(),
                    state.receivedAt(), "HEARTBEAT_TIMEOUT");
            publishGatewayChange(expired);
            return expired;
        });
        Duration cameraMaxAge = Duration.ofSeconds(Math.max(1, cameraMaxAgeSeconds));
        cameras.replaceAll((cameraId, state) -> {
            if ("UNKNOWN".equals(state.health()) || state.receivedAt().plus(cameraMaxAge).isAfter(now)) {
                return state;
            }
            CameraState expired = new CameraState(state.gatewayId(), cameraId, "UNKNOWN", state.sequence(),
                    state.checkedAt(), state.receivedAt(), "STATUS_EXPIRED");
            publishCameraChange(expired);
            return expired;
        });
    }

    private Map<String, Object> cameraView(String cameraId, String gatewayId) {
        GatewayState gateway = gateways.get(gatewayId);
        CameraState camera = cameras.get(cameraId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cameraId", cameraId);
        result.put("gatewayId", gatewayId);
        result.put("gatewayHealth", gateway == null ? health("UNKNOWN", null, "STATUS_MISSING")
                : health(gateway.status(), gateway.receivedAt(), gateway.reasonCode()));
        result.put("streamHealth", camera == null || !gatewayId.equals(camera.gatewayId())
                ? health("UNKNOWN", null, "STATUS_MISSING")
                : health(camera.health(), camera.checkedAt(), camera.reasonCode()));
        return result;
    }

    private Map<String, Object> health(String status, Instant observedAt, String reasonCode) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", status);
        result.put("observedAt", observedAt == null ? null : observedAt.toString());
        result.put("reasonCode", reasonCode == null || reasonCode.isBlank() ? null : reasonCode);
        return result;
    }

    private void publishGatewayChange(GatewayState state) {
        webSocketPublisher.publish("fixed-camera.health.changed", Map.of(
                "scope", "GATEWAY", "gatewayId", state.gatewayId(), "status", state.status()));
    }

    private void publishCameraChange(CameraState state) {
        webSocketPublisher.publish("fixed-camera.health.changed", Map.of(
                "scope", "CAMERA", "gatewayId", state.gatewayId(), "cameraId", state.cameraId(),
                "status", state.health()));
    }

    private boolean stale(long sequence, Instant time, long previousSequence, Instant previousTime) {
        return sequence > 0 && previousSequence > 0 && sequence <= previousSequence
                && (time == null || previousTime == null || !time.isAfter(previousTime));
    }

    private String enumValue(JsonNode root, String field, String... allowed) {
        String value = text(root, field).toUpperCase(Locale.ROOT);
        for (String item : allowed) {
            if (item.equals(value)) {
                return value;
            }
        }
        return null;
    }

    private String text(JsonNode root, String field) {
        JsonNode value = root == null ? null : root.get(field);
        return value == null || value.isNull() ? "" : value.asText("").trim();
    }

    private long longValue(JsonNode root, String field) {
        JsonNode value = root == null ? null : root.get(field);
        return value == null ? 0 : value.asLong(0);
    }

    private Instant instant(JsonNode root, String field) {
        try {
            String value = text(root, field);
            return value.isBlank() ? null : Instant.parse(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String firstString(Map<String, Object> source, String... fields) {
        for (String field : fields) {
            Object value = source.get(field);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private record GatewayState(String gatewayId, String status, long sequence, Instant reportedAt,
                                Instant receivedAt, String reasonCode) {}

    private record CameraState(String gatewayId, String cameraId, String health, long sequence, Instant checkedAt,
                               Instant receivedAt, String reasonCode) {}
}
