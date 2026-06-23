package com.robot.mediaserver.robot.service;

import com.robot.mediaserver.config.MediaProperties;
import com.robot.mediaserver.robot.dto.RobotCameraResponse;
import com.robot.mediaserver.robot.dto.RobotDeviceResponse;
import com.robot.mediaserver.ws.MediaWebSocketPublisher;
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

    private final MediaProperties properties;
    private final MediaWebSocketPublisher webSocketPublisher;
    private final Map<String, RobotDevice> devices = new ConcurrentHashMap<>();

    public RobotRegistryService(MediaProperties properties, MediaWebSocketPublisher webSocketPublisher) {
        this.properties = properties;
        this.webSocketPublisher = webSocketPublisher;
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
            List<RobotCameraResponse> cameras) {
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
        state.put("timestamp", now().toString());
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
                device.lastHeartbeatAt == null ? null : device.lastHeartbeatAt.toString());
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

        private RobotDevice(String robotId) {
            this.robotId = robotId;
            this.name = robotId;
            this.type = "机器人";
        }
    }
}
