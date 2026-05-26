package com.robot.mediaserver.robot.service;

import com.robot.mediaserver.config.MediaProperties;
import com.robot.mediaserver.robot.dto.RobotCameraResponse;
import com.robot.mediaserver.robot.dto.RobotDeviceResponse;
import com.robot.mediaserver.ws.MediaWebSocketPublisher;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
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
        device.lastHeartbeatAt = now();
        if (cameras != null && !cameras.isEmpty()) {
            device.cameras = new ArrayList<>(cameras);
        }
        webSocketPublisher.publish("robot.client." + device.status, toResponse(device));
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
                webSocketPublisher.publish("robot.client.offline", toResponse(device));
            }
        });
    }

    private RobotDeviceResponse toResponse(RobotDevice device) {
        return new RobotDeviceResponse(
                device.robotId,
                device.clientId,
                device.name,
                device.type,
                device.battery,
                device.status,
                device.lastHeartbeatAt,
                device.cameras);
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
        private OffsetDateTime lastHeartbeatAt;
        private List<RobotCameraResponse> cameras = List.of();

        private RobotDevice(String robotId) {
            this.robotId = robotId;
            this.name = robotId;
            this.type = "机器人";
        }
    }
}
