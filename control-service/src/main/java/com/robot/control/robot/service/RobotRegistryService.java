package com.robot.control.robot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.control.config.ControlServiceProperties;
import com.robot.control.config.DateTimeConfig;
import com.robot.control.robot.dto.RobotCameraResponse;
import com.robot.control.robot.dto.RobotDeviceResponse;
import com.robot.control.ws.MediaWebSocketPublisher;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Control Service 本地机器人在线状态注册表。
 *
 * @author leelay
 * @date 2026-07-05
 */
@Service
public class RobotRegistryService {

    private static final String EDGE_DEVICE_STATUS_SOURCE = "EDGE_DEVICE_STATUS";
    private static final String MEDIA_CLIENT_STATUS_SOURCE = "MEDIA_CLIENT_STATUS";

    private static final List<String> DYNAMIC_STATE_FIELDS = List.of(
            "speed",
            "moving",
            "totalMileage",
            "currentMileage",
            "location",
            "runningStatus",
            "healthStatus",
            "chargingStatus",
            "softStopActive",
            "remoteControlEnabled",
            "taskProgressPercent",
            "edgeStatus",
            "edgeMessageId",
            "edgeSchemaVersion",
            "stateSource");

    private final ControlServiceProperties properties;
    private final MediaWebSocketPublisher webSocketPublisher;
    private final ObjectMapper objectMapper;
    private final Map<String, RobotDevice> devices = new ConcurrentHashMap<>();

    /**
     * 创建 RobotRegistryService 实例。
     *
     * @param properties 服务配置
     * @param webSocketPublisher webSocketPublisher
     * @param objectMapper JSON 编解码器
     */
    public RobotRegistryService(
            ControlServiceProperties properties,
            MediaWebSocketPublisher webSocketPublisher,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.webSocketPublisher = webSocketPublisher;
        this.objectMapper = objectMapper;
    }

    /**
     * 根据机器人客户端上报更新注册表。
     *
     * @param data 业务数据
     * @return 是否从离线变为在线
     */
    public boolean update(Map<String, Object> data) {
        String robotId = string(data.get("robotId"), "");
        String clientId = string(data.get("clientId"), "");
        String status = string(data.get("status"), "");
        String name = string(data.get("name"), robotId);
        String type = string(data.get("type"), null);
        String typeCode = string(data.get("typeCode"), null);
        String controlMode = string(data.get("controlMode"), null);
        Long stateSeq = data.get("stateSeq") instanceof Number seqValue ? seqValue.longValue() : null;
        String missionStatus = string(data.get("missionStatus"), "IDLE");
        String navigationStatus = string(data.get("navigationStatus"), "IDLE");
        Object controlOwner = data.get("controlOwner");
        Boolean estopActive = data.get("estopActive") instanceof Boolean estopValue ? estopValue : null;
        Integer battery = data.get("battery") instanceof Number batteryValue ? batteryValue.intValue() : null;
        List<RobotCameraResponse> cameras = objectMapper.convertValue(
                data.getOrDefault("cameras", List.of()),
                objectMapper.getTypeFactory().constructCollectionType(List.class, RobotCameraResponse.class));
        List<Map<String, Object>> mountedDevices = objectMapper.convertValue(
                data.getOrDefault("devices", List.of()),
                objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
        return update(
                robotId,
                clientId,
                status,
                name,
                type,
                typeCode,
                battery,
                controlMode,
                stateSeq,
                missionStatus,
                navigationStatus,
                controlOwner,
                estopActive,
                cameras,
                mountedDevices,
                data);
    }

    /**
     * 根据机器人客户端上报更新注册表。
     *
     * @param robotId 机器人 ID
     * @param clientId 客户端 ID
     * @param status 状态消息
     * @param name 名称
     * @param type type
     * @param battery battery
     * @param controlMode 控制模式
     * @param stateSeq stateSeq
     * @param missionStatus missionStatus
     * @param navigationStatus navigationStatus
     * @param controlOwner controlOwner
     * @param estopActive estopActive
     * @param cameras cameras
     * @param mountedDevices mountedDevices
     * @return 是否从离线变为在线
     */
    public boolean update(
            String robotId,
            String clientId,
            String status,
            String name,
            String type,
            Integer battery,
            String controlMode,
            Long stateSeq,
            String missionStatus,
            String navigationStatus,
            Object controlOwner,
            Boolean estopActive,
            List<RobotCameraResponse> cameras,
            List<Map<String, Object>> mountedDevices) {
        return update(
                robotId,
                clientId,
                status,
                name,
                type,
                null,
                battery,
                controlMode,
                stateSeq,
                missionStatus,
                navigationStatus,
                controlOwner,
                estopActive,
                cameras,
                mountedDevices);
    }

    public boolean update(
            String robotId,
            String clientId,
            String status,
            String name,
            String type,
            String typeCode,
            Integer battery,
            String controlMode,
            Long stateSeq,
            String missionStatus,
            String navigationStatus,
            Object controlOwner,
            Boolean estopActive,
            List<RobotCameraResponse> cameras,
            List<Map<String, Object>> mountedDevices) {
        return update(
                robotId,
                clientId,
                status,
                name,
                type,
                typeCode,
                battery,
                controlMode,
                stateSeq,
                missionStatus,
                navigationStatus,
                controlOwner,
                estopActive,
                cameras,
                mountedDevices,
                Map.of());
    }

    private boolean update(
            String robotId,
            String clientId,
            String status,
            String name,
            String type,
            String typeCode,
            Integer battery,
            String controlMode,
            Long stateSeq,
            String missionStatus,
            String navigationStatus,
            Object controlOwner,
            Boolean estopActive,
            List<RobotCameraResponse> cameras,
            List<Map<String, Object>> mountedDevices,
            Map<String, Object> dynamicState) {
        if (robotId == null || robotId.isBlank()) {
            return false;
        }
        RobotDevice device = devices.computeIfAbsent(robotId, RobotDevice::new);
        Object stateSource = dynamicState.get("stateSource");
        boolean edgeStatusReport = EDGE_DEVICE_STATUS_SOURCE.equals(stateSource);
        boolean becameOnline;
        boolean publishState;
        Map<String, Object> state;
        synchronized (device) {
            // 在取得设备锁后记录处理时间，保证它晚于已经完成的离线扫描版本，避免并发事件时间倒退。
            OffsetDateTime receivedAt = now();
            boolean wasConnected = "online".equals(device.status) || "fault".equals(device.status);
            device.clientId = clientId;
            device.name = blank(name) ? robotId : name;
            String reportedTypeCode = reportedTypeCode(typeCode, type);
            String reportedType = reportedType(type);
            if (!blank(reportedTypeCode)) {
                device.typeCode = reportedTypeCode;
            }
            if (!blank(reportedType)) {
                device.type = reportedType;
            }
            if (edgeStatusReport && battery != null) {
                device.battery = Math.max(0, Math.min(100, battery));
            }
            if (edgeStatusReport) {
                String normalizedStatus = normalizedStatus(status);
                if (device.lastEdgeStatusAt == null || !normalizedStatus.equals(device.status)) {
                    device.statusChangedAt = receivedAt;
                }
                device.status = normalizedStatus;
                device.lastEdgeStatusAt = receivedAt;
            }
            if (edgeStatusReport && dynamicState.containsKey("controlMode")) {
                device.controlMode = normalizedControlMode(controlMode);
            }
            if (stateSeq != null) {
                device.stateSeq = stateSeq;
            }
            device.missionStatus = blank(missionStatus) ? "IDLE" : missionStatus;
            device.navigationStatus = blank(navigationStatus) ? "IDLE" : navigationStatus;
            device.controlOwner = controlOwner;
            device.estopActive = estopActive == null ? false : estopActive;
            device.lastHeartbeatAt = receivedAt;
            if (cameras != null && !cameras.isEmpty()) {
                device.cameras = new ArrayList<>(cameras);
            }
            if (mountedDevices != null && !mountedDevices.isEmpty()) {
                device.mountedDevices = new ArrayList<>(mountedDevices);
            }
            DYNAMIC_STATE_FIELDS.forEach(field -> {
                if ("speed".equals(field) && !edgeStatusReport) {
                    return;
                }
                if (dynamicState.containsKey(field) && dynamicState.get(field) != null) {
                    device.dynamicState.put(field, dynamicState.get(field));
                }
            });
            becameOnline = !wasConnected && ("online".equals(device.status) || "fault".equals(device.status));
            // 媒体客户端只补充摄像头等附属信息，首个边缘状态到达前没有在线状态真值。
            // 此时保留注册表快照为离线，但不把默认值作为实时状态广播给页面。
            publishState = !MEDIA_CLIENT_STATUS_SOURCE.equals(stateSource) || device.lastEdgeStatusAt != null;
            state = toState(device, receivedAt);
        }
        if (publishState) {
            webSocketPublisher.publish("robot.state", state);
        }
        return becameOnline;
    }

    /**
     * 列出当前注册的机器人状态。
     *
     * @return 列表结果
     */
    public List<RobotDeviceResponse> list() {
        return devices.values().stream()
                .sorted(Comparator.comparing(device -> device.robotId))
                .map(this::toResponse)
                .toList();
    }

    /**
     * 移除本地注册表中的机器人，并向前端广播离线状态。
     *
     * @param robotId 机器人 ID
     */
    public void remove(String robotId) {
        if (robotId == null || robotId.isBlank()) {
            return;
        }
        RobotDevice removed = devices.remove(robotId);
        if (removed == null) {
            return;
        }
        Map<String, Object> state;
        synchronized (removed) {
            OffsetDateTime changedAt = now();
            removed.status = "offline";
            removed.statusChangedAt = changedAt;
            removed.dynamicState.put("stateSource", "UNREGISTERED_DEVICE");
            state = toState(removed, changedAt);
        }
        webSocketPublisher.publish("robot.state", state);
    }

    /** Returns the latest in-memory state for one robot. */
    public Optional<RobotDeviceResponse> find(String robotId) {
        RobotDevice device = devices.get(robotId);
        return device == null ? Optional.empty() : Optional.of(toResponse(device));
    }

    /**
     * 扫描并标记心跳超时的机器人。
     */
    public void sweepOffline() {
        OffsetDateTime sweepAt = now();
        OffsetDateTime threshold = sweepAt.minusSeconds(properties.getRobot().getHeartbeatTimeoutSeconds());
        List<Map<String, Object>> offlineEvents = new ArrayList<>();
        devices.values().forEach(device -> {
            synchronized (device) {
                boolean connected = "online".equals(device.status) || "fault".equals(device.status);
                if (connected
                        && device.lastEdgeStatusAt != null
                        && device.lastEdgeStatusAt.isBefore(threshold)) {
                    device.status = "offline";
                    device.statusChangedAt = sweepAt;
                    device.dynamicState.put("stateSource", "OFFLINE_SCAN");
                    offlineEvents.add(toState(device, sweepAt));
                }
            }
        });
        offlineEvents.forEach(state -> webSocketPublisher.publish("robot.state", state));
        cleanupStaleOffline();
    }

    /**
     * 清理长期离线（超过 {@code control.robot.offline-retention-seconds}）的注册表条目，
     * 避免未注册或已下架设备的内存条目无限累积。
     */
    private void cleanupStaleOffline() {
        OffsetDateTime retentionThreshold = now().minusSeconds(properties.getRobot().getOfflineRetentionSeconds());
        devices.entrySet().removeIf(entry -> {
            RobotDevice device = entry.getValue();
            return "offline".equals(device.status)
                    && device.lastHeartbeatAt != null
                    && device.lastHeartbeatAt.isBefore(retentionThreshold);
        });
    }

    /**
     * 转换为 WebSocket 推送状态。
     *
     * @param device device
     * @return WebSocket 状态载荷
     */
    private Map<String, Object> toState(RobotDevice device, OffsetDateTime eventAt) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("robotId", device.robotId);
        state.put("clientId", device.clientId == null ? "" : device.clientId);
        state.put("name", device.name);
        state.put("type", device.type);
        state.put("typeCode", device.typeCode);
        state.put("battery", device.battery);
        state.put("speed", device.dynamicState.get("speed"));
        state.put("runtimeUpdatedAt", runtimeUpdatedAt(device));
        state.put("status", device.status);
        state.put("statusChangedAt", device.statusChangedAt.toString());
        state.put("controlMode", device.controlMode);
        state.put("controlModeName", controlModeName(device.controlMode));
        state.put("stateSeq", device.stateSeq);
        state.put("missionStatus", device.missionStatus);
        state.put("navigationStatus", device.navigationStatus);
        state.put("controlOwner", device.controlOwner);
        state.put("estopActive", device.estopActive);
        state.put("cameras", device.cameras);
        state.put("devices", device.mountedDevices);
        state.putAll(device.dynamicState);
        state.put("timestamp", DateTimeConfig.format(eventAt));
        return state;
    }

    private String normalizedStatus(String status) {
        if ("offline".equalsIgnoreCase(status)) {
            return "offline";
        }
        if ("fault".equalsIgnoreCase(status) || "error".equalsIgnoreCase(status)) {
            return "fault";
        }
        return "online";
    }

    /**
     * 转换为机器人状态响应。
     *
     * @param device device
     * @return 机器人状态响应
     */
    private RobotDeviceResponse toResponse(RobotDevice device) {
        synchronized (device) {
            return new RobotDeviceResponse(
                    device.robotId,
                    device.clientId,
                    device.name,
                    device.type,
                    device.typeCode,
                    device.battery,
                    device.status,
                    device.statusChangedAt.toString(),
                    device.controlMode,
                    controlModeName(device.controlMode),
                    device.stateSeq,
                    device.missionStatus,
                    device.navigationStatus,
                    device.controlOwner,
                    device.estopActive,
                    device.lastHeartbeatAt,
                    List.copyOf(device.cameras),
                    List.copyOf(device.mountedDevices),
                    string(device.dynamicState.get("healthStatus"), null),
                    DateTimeConfig.format(device.lastHeartbeatAt),
                    device.dynamicState.get("speed") instanceof Number speed ? speed.doubleValue() : null,
                    runtimeUpdatedAt(device));
        }
    }

    private String controlModeName(String controlMode) {
        return normalizedControlMode(controlMode);
    }

    private String reportedTypeCode(String typeCode, String type) {
        if (!blank(typeCode)) {
            return typeCode.trim();
        }
        return isTypeCode(type) ? type.trim() : null;
    }

    private String reportedType(String type) {
        return blank(type) || isTypeCode(type) ? null : type.trim();
    }

    private boolean isTypeCode(String type) {
        if (blank(type)) {
            return false;
        }
        String value = type.trim();
        return value.equals(value.toUpperCase()) && (value.contains("_") || value.matches("[A-Z0-9]+"));
    }

    private String normalizedControlMode(String controlMode) {
        String mode = controlMode == null ? "" : controlMode.trim();
        if ("导航模式".equals(mode)) return mode;
        return "手动模式".equals(mode) || "常规模式".equals(mode) ? "手动模式" : null;
    }

    private String runtimeUpdatedAt(RobotDevice device) {
        return device.lastEdgeStatusAt == null ? null : device.lastEdgeStatusAt.toString();
    }

    /**
     * 读取字符串值并应用默认值。
     *
     * @param value 待处理值
     * @param defaultValue 默认值
     * @return 字符串值
     */
    private String string(Object value, String defaultValue) {
        return value == null || String.valueOf(value).isBlank() ? defaultValue : String.valueOf(value);
    }

    /**
     * 判断字符串是否为空白。
     *
     * @param value 待处理值
     * @return 是否为空白
     */
    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * 返回当前时间。
     *
     * @return 当前时间
     */
    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    /**
     * 内存中的单台机器人状态快照。
     *
     * @author leelay
     * @date 2026-07-05
     */
    private static class RobotDevice {
        private final String robotId;
        private String clientId;
        private String name;
        private String type;
        private String typeCode;
        private Integer battery;
        private String status = "offline";
        private OffsetDateTime statusChangedAt = OffsetDateTime.now(ZoneOffset.UTC);
        private String controlMode;
        private Long stateSeq = 1L;
        private String missionStatus = "IDLE";
        private String navigationStatus = "IDLE";
        private Object controlOwner;
        private Boolean estopActive = false;
        private OffsetDateTime lastHeartbeatAt;
        private OffsetDateTime lastEdgeStatusAt;
        private List<RobotCameraResponse> cameras = List.of();
        private List<Map<String, Object>> mountedDevices = List.of();
        private Map<String, Object> dynamicState = new LinkedHashMap<>();

        /**
         * 创建 RobotDevice 实例。
         *
         * @param robotId 机器人 ID
         */
        private RobotDevice(String robotId) {
            this.robotId = robotId;
            this.name = robotId;
        }
    }
}
