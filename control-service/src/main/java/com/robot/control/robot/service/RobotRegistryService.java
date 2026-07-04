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
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class RobotRegistryService {

    private final ControlServiceProperties properties;
    private final MediaWebSocketPublisher webSocketPublisher;
    private final ObjectMapper objectMapper;
    private final Map<String, RobotDevice> devices = new ConcurrentHashMap<>();

    public RobotRegistryService(
            ControlServiceProperties properties,
            MediaWebSocketPublisher webSocketPublisher,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.webSocketPublisher = webSocketPublisher;
        this.objectMapper = objectMapper;
    }

    public boolean update(Map<String, Object> data) {
        String robotId = string(data.get("robotId"), "");
        String clientId = string(data.get("clientId"), "");
        String status = string(data.get("status"), "");
        String name = string(data.get("name"), robotId);
        String type = string(data.get("type"), "机器人");
        String controlMode = string(data.get("controlMode"), "MANUAL");
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
            Integer battery,
            String controlMode,
            Long stateSeq,
            String missionStatus,
            String navigationStatus,
            Object controlOwner,
            Boolean estopActive,
            List<RobotCameraResponse> cameras,
            List<Map<String, Object>> mountedDevices) {
        if (robotId == null || robotId.isBlank()) {
            return false;
        }
        RobotDevice device = devices.computeIfAbsent(robotId, RobotDevice::new);
        boolean becameOnline = !"online".equals(device.status) && !"offline".equalsIgnoreCase(status);
        device.clientId = clientId;
        device.name = blank(name) ? robotId : name;
        device.type = blank(type) ? "机器人" : type;
        if (battery != null) {
            device.battery = Math.max(0, Math.min(100, battery));
        }
        device.status = "offline".equalsIgnoreCase(status) ? "offline" : "online";
        device.controlMode = blank(controlMode) ? "MANUAL" : controlMode;
        if (stateSeq != null) {
            device.stateSeq = stateSeq;
        }
        device.missionStatus = blank(missionStatus) ? "IDLE" : missionStatus;
        device.navigationStatus = blank(navigationStatus) ? "IDLE" : navigationStatus;
        device.controlOwner = controlOwner;
        device.estopActive = estopActive == null ? false : estopActive;
        device.lastHeartbeatAt = now();
        if (cameras != null && !cameras.isEmpty()) {
            device.cameras = new ArrayList<>(cameras);
        }
        if (mountedDevices != null && !mountedDevices.isEmpty()) {
            device.mountedDevices = new ArrayList<>(mountedDevices);
        }
        webSocketPublisher.publish("robot.state", toState(device));
        return becameOnline;
    }

    public List<RobotDeviceResponse> list() {
        return devices.values().stream()
                .sorted(Comparator.comparing(device -> device.robotId))
                .map(this::toResponse)
                .toList();
    }

    public void sweepOffline() {
        OffsetDateTime threshold = now().minusSeconds(properties.getRobot().getHeartbeatTimeoutSeconds());
        devices.values().forEach(device -> {
            if ("online".equals(device.status) && device.lastHeartbeatAt != null && device.lastHeartbeatAt.isBefore(threshold)) {
                device.status = "offline";
                webSocketPublisher.publish("robot.state", toState(device));
            }
        });
    }

    private Map<String, Object> toState(RobotDevice device) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("robotId", device.robotId);
        state.put("clientId", device.clientId == null ? "" : device.clientId);
        state.put("name", device.name);
        state.put("type", device.type);
        state.put("battery", device.battery == null ? 0 : device.battery);
        state.put("status", device.status);
        state.put("controlMode", device.controlMode);
        state.put("stateSeq", device.stateSeq);
        state.put("missionStatus", device.missionStatus);
        state.put("navigationStatus", device.navigationStatus);
        state.put("controlOwner", device.controlOwner);
        state.put("estopActive", device.estopActive);
        state.put("cameras", device.cameras);
        state.put("devices", device.mountedDevices);
        state.put("timestamp", DateTimeConfig.format(now()));
        return state;
    }

    private RobotDeviceResponse toResponse(RobotDevice device) {
        return new RobotDeviceResponse(
                device.robotId,
                device.clientId,
                device.name,
                device.type,
                device.battery,
                device.status,
                device.controlMode,
                device.stateSeq,
                device.missionStatus,
                device.navigationStatus,
                device.controlOwner,
                device.estopActive,
                device.lastHeartbeatAt,
                device.cameras,
                device.mountedDevices,
                DateTimeConfig.format(device.lastHeartbeatAt));
    }

    private String string(Object value, String defaultValue) {
        return value == null || String.valueOf(value).isBlank() ? defaultValue : String.valueOf(value);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private static class RobotDevice {
        private final String robotId;
        private String clientId;
        private String name;
        private String type;
        private Integer battery;
        private String status = "offline";
        private String controlMode = "MANUAL";
        private Long stateSeq = 1L;
        private String missionStatus = "IDLE";
        private String navigationStatus = "IDLE";
        private Object controlOwner;
        private Boolean estopActive = false;
        private OffsetDateTime lastHeartbeatAt;
        private List<RobotCameraResponse> cameras = List.of();
        private List<Map<String, Object>> mountedDevices = List.of();

        private RobotDevice(String robotId) {
            this.robotId = robotId;
            this.name = robotId;
            this.type = "机器人";
        }
    }
}
