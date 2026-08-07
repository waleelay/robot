package com.robot.control.messaging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.control.config.DateTimeConfig;
import com.robot.control.robot.service.RobotRegistryService;
import com.robot.control.service.EquipmentControlService;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 处理平台边缘设备状态上报，并转换为控制服务统一机器人状态。
 */
@Component
public class EdgeDeviceStatusHandler {

    private static final Logger log = LoggerFactory.getLogger(EdgeDeviceStatusHandler.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;
    private final EquipmentControlService equipmentControlService;
    private final RobotRegistryService robotRegistryService;

    public EdgeDeviceStatusHandler(
            ObjectMapper objectMapper,
            EquipmentControlService equipmentControlService,
            RobotRegistryService robotRegistryService) {
        this.objectMapper = objectMapper;
        this.equipmentControlService = equipmentControlService;
        this.robotRegistryService = robotRegistryService;
    }

    /**
     * 处理一条 {@code eiop/v1/edge/{serialNumber}/status} 消息。
     *
     * @param topic MQTT topic
     * @param json MQTT payload
     */
    public void handle(String topic, String json) {
        try {
            String serialNumber = serialNumberFromTopic(topic);
            Map<String, Object> envelope = objectMapper.readValue(json, MAP_TYPE);
            String messageType = string(envelope.get("messageType"));
            if (!messageType.isBlank() && !"DEVICE_STATUS_REPORT".equals(messageType)) {
                log.debug("Ignore unsupported edge message type={} topic={}", messageType, topic);
                return;
            }
            Map<String, Object> payload = map(envelope.get("payload"));
            Map<String, Object> status = map(payload.get("status"));
            if (status.isEmpty()) {
                log.debug("Ignore edge status without payload.status topic={}", topic);
                return;
            }

            Map<String, Object> update = normalize(serialNumber, envelope, status);
            Map<String, Object> merged = equipmentControlService.mergeEdgeDeviceStatus(serialNumber, update);
            robotRegistryService.update(merged);
        } catch (Exception ex) {
            log.warn("Failed to handle edge device status topic={}, payload={}", topic, json, ex);
        }
    }

    private Map<String, Object> normalize(
            String serialNumber,
            Map<String, Object> envelope,
            Map<String, Object> status) {
        Map<String, Object> basic = map(status.get("basic"));
        Map<String, Object> motion = map(status.get("motion"));
        Map<String, Object> localization = map(status.get("localization"));
        Map<String, Object> energy = map(status.get("energy"));
        Map<String, Object> control = map(status.get("control"));
        Map<String, Object> task = map(status.get("task"));

        String timestamp = String.valueOf(
                DateTimeConfig.normalize(envelope.getOrDefault("timestamp", OffsetDateTime.now())));
        Map<String, Object> update = new LinkedHashMap<>();
        update.put("robotId", serialNumber);
        update.put("status", "online");
        putIfPresent(update, "battery", energy.get("batteryPercent"));
        putIfPresent(update, "speed", motion.get("speed"));
        putIfPresent(update, "moving", motion.get("moving"));
        putIfPresent(update, "runningStatus", basic.get("runningStatus"));
        putIfPresent(update, "healthStatus", basic.get("healthStatus"));
        putIfPresent(update, "chargingStatus", energy.get("chargingStatus"));
        putIfPresent(update, "controlMode", normalizeControlMode(control.get("controlMode")));
        putIfPresent(update, "estopActive", control.get("emergencyStop"));
        putIfPresent(update, "softStopActive", control.get("softStop"));
        putIfPresent(update, "remoteControlEnabled", control.get("remoteControlEnabled"));
        putIfPresent(update, "missionStatus", normalizeTaskStatus(task.get("taskStatus")));
        putIfPresent(update, "taskProgressPercent", task.get("progressPercent"));
        if (!localization.isEmpty()) {
            Map<String, Object> location = new LinkedHashMap<>();
            putIfPresent(location, "localized", localization.get("localized"));
            putIfPresent(location, "coordinateType", localization.get("coordinateType"));
            putIfPresent(location, "mapId", localization.get("mapId"));
            putIfPresent(location, "x", localization.get("coordinateX"));
            putIfPresent(location, "y", localization.get("coordinateY"));
            putIfPresent(location, "z", localization.get("coordinateZ"));
            putIfPresent(location, "yaw", localization.get("yaw"));
            location.put("updatedAt", timestamp);
            update.put("location", location);
        }
        update.put("edgeStatus", new LinkedHashMap<>(status));
        update.put("edgeMessageId", envelope.get("messageId"));
        update.put("edgeSchemaVersion", envelope.get("schemaVersion"));
        update.put("stateSource", "EDGE_DEVICE_STATUS");
        update.put("timestamp", timestamp);
        return update;
    }

    private String serialNumberFromTopic(String topic) {
        String[] parts = topic == null ? new String[0] : topic.split("/");
        if (parts.length != 5
                || !"eiop".equals(parts[0])
                || !"v1".equals(parts[1])
                || !"edge".equals(parts[2])
                || parts[3].isBlank()
                || !"status".equals(parts[4])) {
            throw new IllegalArgumentException("无效的边缘设备状态 topic：" + topic);
        }
        return parts[3];
    }

    private String normalizeControlMode(Object value) {
        String mode = string(value).trim();
        if (mode.isBlank()) {
            return null;
        }
        if ("手动模式".equals(mode)) {
            return "手动模式";
        }
        if ("导航模式".equals(mode)) {
            return "导航模式";
        }
        return null;
    }

    private String normalizeTaskStatus(Object value) {
        String status = string(value).trim();
        String normalized = status.toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return null;
        }
        if (normalized.contains("IDLE") || normalized.contains("空闲") || normalized.contains("待机")) {
            return "IDLE";
        }
        if (normalized.contains("RUNNING") || normalized.contains("EXECUTING") || normalized.contains("执行中")) {
            return "RUNNING";
        }
        if (normalized.contains("COMPLETED") || normalized.contains("FINISHED") || normalized.contains("已完成")) {
            return "COMPLETED";
        }
        if (normalized.contains("PAUSED") || normalized.contains("暂停")) {
            return "PAUSED";
        }
        if (normalized.contains("FAILED") || normalized.contains("ERROR") || normalized.contains("失败")) {
            return "FAILED";
        }
        return status;
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null && !String.valueOf(value).isBlank()) {
            target.put(key, value);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map
                ? new LinkedHashMap<>((Map<String, Object>) map)
                : new LinkedHashMap<>();
    }

    private String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
