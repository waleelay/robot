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
    /**
     * 按 Gateway ID 保存最近接受的状态、序列号及本地接收时间。
     */
    private final Map<String, GatewayState> gateways = new ConcurrentHashMap<>();
    /**
     * 按摄像头 ID 保存最近接受的 RTSP 探测事实。
     */
    private final Map<String, CameraState> cameras = new ConcurrentHashMap<>();
    /**
     * 网关恢复后是否仍需执行一次批量视频恢复，失败后保留重试意图。
     */
    private final AtomicBoolean pendingGatewayRecovery = new AtomicBoolean();
    /**
     * 待重试恢复的摄像头 ID 集合，按视频源去重。
     */
    private final Set<String> pendingCameraRecoveries = ConcurrentHashMap.newKeySet();
    /**
     * 下一次允许恢复重试的服务端时间戳，单位毫秒。
     */
    private volatile long recoveryRetryAfterMillis;
    private ControlMediaServiceClient mediaServiceClient;

    /**
     * 网关健康状态转为离线的接收间隔阈值，单位秒。
     */
    @Value("${control.fixed-camera-health.gateway-timeout-seconds:30}")
    private long gatewayTimeoutSeconds = 30;

    /**
     * 摄像头健康事实允许的最大陈旧时长，单位秒。
     */
    @Value("${control.fixed-camera-health.camera-max-age-seconds:120}")
    private long cameraMaxAgeSeconds = 120;

    /**
     * 初始化 FixedCameraHealthService，保存所需依赖及初始运行状态。
     *
     * @param objectMapper JSON 编解码器
     * @param webSocketPublisher 向已连接客户端投递业务事件的组件
     * @param videoCommandService 控制侧视频操作编排服务。
     */
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

    /**
     * 合并 Gateway 健康上报，按序列号和接收时间维护有效事实。
     *
     * @param topic MQTT 主题
     * @param payload 消息载荷
     */
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
                log.warn("已拒绝网关标识 与主题不一致的固定摄像头状态，主题网关={} 载荷网关={}",
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

    /**
     * 合并摄像头码流健康上报，拒绝不属于当前 Gateway 或陈旧的状态。
     *
     * @param topic MQTT 主题
     * @param payload 消息载荷
     */
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
                log.warn("已拒绝摄像头或网关标识 与主题不一致的健康消息，主题={}", topic);
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

    /**
     * 仅为当前授权摄像头组装健康快照，区分离线、未知与未配置。
     *
     * @param authorizedCameras 当前用户有权访问的固定摄像头档案
     * @param defaultGatewayId 摄像头未声明 Gateway 时使用的默认标识
     * @return 带版本和观测时间的授权摄像头健康快照
     */
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

    /**
     * 合并网关和摄像头恢复意图，失败后保留待恢复项并延迟重试。
     */
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
                log.warn("固定摄像头 网关恢复后的推流收敛失败，稍后重试", exception);
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

    /**
     * Gateway 状态及消息序号、报告时间和本地接收时间。
     *
     * @param gatewayId 目标固定摄像头 Gateway ID
     * @param status 当前业务状态，取值遵循所属模型的状态协议
     * @param sequence 上游状态序列号，用于拒绝重复或乱序更新
     * @param reportedAt 设备声明的状态上报时间
     * @param receivedAt 服务端实际接收该状态的时间
     * @param reasonCode 当前状态或失败原因码
     */
    private record GatewayState(String gatewayId, String status, long sequence, Instant reportedAt,
                                Instant receivedAt, String reasonCode) {}

    /**
     * 摄像头健康状态及序号、探测时间和本地接收时间。
     *
     * @param gatewayId 目标固定摄像头 Gateway ID
     * @param cameraId 固定摄像头 ID
     * @param health 摄像头健康状态编码
     * @param sequence 上游状态序列号，用于拒绝重复或乱序更新
     * @param checkedAt Gateway 最近一次完成摄像头检查的时间
     * @param receivedAt 服务端实际接收该状态的时间
     * @param reasonCode 当前状态或失败原因码
     */
    private record CameraState(String gatewayId, String cameraId, String health, long sequence, Instant checkedAt,
                               Instant receivedAt, String reasonCode) {}
}
