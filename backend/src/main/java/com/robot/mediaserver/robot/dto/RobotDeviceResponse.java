package com.robot.mediaserver.robot.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record RobotDeviceResponse(
        String robotId,
        String clientId,
        String name,
        String type,
        String status,
        OffsetDateTime lastHeartbeatAt,
        List<RobotCameraResponse> cameras) {
}
