package com.robot.control.fixedcamera;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.control.service.ControlVideoCommandService;
import com.robot.control.client.ControlMediaServiceClient;
import com.robot.control.ws.MediaWebSocketPublisher;
import com.robot.media.common.video.FixedCameraIngressResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** 保存固定摄像头 Gateway 与 RTSP 最近健康状态。 */
@Service
public class FixedCameraHealthService {

    private static final Logger log = LoggerFactory.getLogger(FixedCameraHealthService.class);
    private static final String VERSION = "1.0";

    private final ObjectMapper objectMapper;
    private final MediaWebSocketPublisher webSocketPublisher;
    private final ControlVideoCommandService videoCommandService;
    private final Map<String, GatewayState> gateways = new ConcurrentHashMap<>();
    private final Map<String, CameraState> cameras = new ConcurrentHashMap<>();
    private final AtomicBoolean pendingGatewayRecovery = new AtomicBoolean();
    private final Set<String> pendingCameraRecoveries = ConcurrentHashMap.newKeySet();
    private volatile long recoveryRetryAfterMillis;
    private ControlMediaServiceClient mediaServiceClient;

    @Value("${control.fixed-camera-health.gateway-timeout-seconds:30}")
    private long gatewayTimeoutSeconds = 30;

    @Value("${control.fixed-camera-health.camera-max-age-seconds:120}")
    private long cameraMaxAgeSeconds = 120;

    public FixedCameraHealthService(
            ObjectMapper objectMapper,
            MediaWebSocketPublisher webSocketPublisher,
            ControlVideoCommandService videoCommandService) {
        this.objectMapper = objectMapper;
        this.webSocketPublisher = webSocketPublisher;
        this.videoCommandService = videoCommandService;
    }

    @Autowired
    void setMediaServiceClient(ControlMediaServiceClient mediaServiceClient) {
        this.mediaServiceClient = mediaServiceClient;
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
            AtomicBoolean becameOnline = new AtomicBoolean();
            gateways.compute(topicGatewayId, (ignored, previous) -> {
                if (previous != null && stale(incoming.sequence(), incoming.reportedAt(), previous.sequence(), previous.reportedAt())
                        && !"OFFLINE".equals(status)) {
                    return previous;
                }
                if (previous == null || !previous.status().equals(incoming.status())) {
                    publishGatewayChange(incoming);
                }
                if (previous != null && !"ONLINE".equals(previous.status()) && "ONLINE".equals(incoming.status())) {
                    becameOnline.set(true);
                }
                return incoming;
            });
            if (becameOnline.get()) {
                pendingGatewayRecovery.set(true);
            }
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
            AtomicBoolean becameAvailable = new AtomicBoolean();
            cameras.compute(topicCameraId, (ignored, previous) -> {
                if (previous != null && stale(incoming.sequence(), incoming.checkedAt(), previous.sequence(), previous.checkedAt())) {
                    return previous;
                }
                if (previous == null || !previous.health().equals(incoming.health())
                        || !previous.reasonCode().equals(incoming.reasonCode())) {
                    publishCameraChange(incoming);
                }
                if (previous != null && !"AVAILABLE".equals(previous.health())
                        && "AVAILABLE".equals(incoming.health())) {
                    becameAvailable.set(true);
                }
                return incoming;
            });
            if (becameAvailable.get()) {
                pendingCameraRecoveries.add(topicCameraId);
            }
        } catch (Exception exception) {
            log.warn("解析固定摄像头健康状态失败，主题={} 载荷字节数={}", topic,
                    payload == null ? 0 : payload.length, exception);
        }
    }

    public Map<String, Object> authorizedSnapshot(List<Map<String, Object>> authorizedCameras, String defaultGatewayId) {
        List<Map<String, Object>> source = authorizedCameras == null ? List.of() : authorizedCameras;
        Map<String, FixedCameraIngressResponse> ingressStatuses = loadIngressStatuses(source);
        List<Map<String, Object>> records = new ArrayList<>();
        for (Map<String, Object> camera : source) {
            String cameraId = firstString(camera, "cameraId", "id");
            if (cameraId == null) {
                continue;
            }
            if ("RTMP".equalsIgnoreCase(firstString(camera, "protocolType"))) {
                records.add(rtmpCameraView(cameraId, ingressStatuses.get(cameraId)));
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

    private Map<String, FixedCameraIngressResponse> loadIngressStatuses(List<Map<String, Object>> cameras) {
        List<String> cameraIds = cameras.stream()
                .filter(camera -> "RTMP".equalsIgnoreCase(firstString(camera, "protocolType")))
                .map(camera -> firstString(camera, "cameraId", "id"))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (cameraIds.isEmpty() || mediaServiceClient == null) {
            return Map.of();
        }
        Map<String, FixedCameraIngressResponse> result = new LinkedHashMap<>();
        for (int offset = 0; offset < cameraIds.size(); offset += 500) {
            List<String> batch = cameraIds.subList(offset, Math.min(offset + 500, cameraIds.size()));
            try {
                List<FixedCameraIngressResponse> statuses = mediaServiceClient.fixedCameraIngressStatuses(batch);
                if (statuses != null) {
                    statuses.forEach(status -> result.put(status.cameraId(), status));
                }
            } catch (RuntimeException exception) {
                log.warn("查询 RTMP 固定摄像头状态失败，本批次降级为 UNKNOWN，数量={}", batch.size(), exception);
            }
        }
        return result;
    }

    private Map<String, Object> rtmpCameraView(String cameraId, FixedCameraIngressResponse status) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cameraId", cameraId);
        result.put("protocolType", "RTMP");
        result.put("gatewayId", null);
        result.put("gatewayHealth", health("UNKNOWN", null, "NOT_APPLICABLE"));
        result.put("configReady", status != null && status.configured());
        if (status == null) {
            result.put("streamHealth", health("UNKNOWN", null, "LIVEKIT_STATUS_STALE"));
            return result;
        }
        String streamStatus = switch (String.valueOf(status.streamStatus()).toUpperCase(Locale.ROOT)) {
            case "ONLINE" -> "AVAILABLE";
            case "OFFLINE" -> "UNAVAILABLE";
            default -> "UNKNOWN";
        };
        result.put("streamHealth", health(streamStatus,
                status.observedAt() == null ? null : status.observedAt().toInstant(), status.reasonCode()));
        return result;
    }

    @Scheduled(fixedDelayString = "${control.fixed-camera-health.sweep-delay-ms:1000}")
    void expireStaleStates() {
        expireStaleStates(Instant.now());
        recoverPendingSources();
    }

    void recoverPendingSources() {
        if (System.currentTimeMillis() < recoveryRetryAfterMillis) {
            return;
        }
        List<String> camerasCoveredByGatewayRecovery = List.copyOf(pendingCameraRecoveries);
        if (pendingGatewayRecovery.compareAndSet(true, false)) {
            try {
                videoCommandService.recoverFixedCameraSources(null, true);
                camerasCoveredByGatewayRecovery.forEach(pendingCameraRecoveries::remove);
            } catch (RuntimeException exception) {
                pendingGatewayRecovery.set(true);
                recoveryRetryAfterMillis = System.currentTimeMillis() + 5000;
                log.warn("固定摄像头 Gateway 恢复后的推流收敛失败，稍后重试", exception);
                return;
            }
        }
        for (String cameraId : List.copyOf(pendingCameraRecoveries)) {
            try {
                videoCommandService.recoverFixedCameraSources(cameraId, false);
                pendingCameraRecoveries.remove(cameraId);
            } catch (RuntimeException exception) {
                recoveryRetryAfterMillis = System.currentTimeMillis() + 5000;
                log.warn("固定摄像头 RTSP 恢复后的推流收敛失败，摄像头={}", cameraId, exception);
            }
        }
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
        result.put("protocolType", "RTSP");
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
