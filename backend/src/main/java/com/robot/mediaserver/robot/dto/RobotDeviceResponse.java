package com.robot.mediaserver.robot.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record RobotDeviceResponse(
        String robotId,
        String clientId,
        String clientVersion,
        String name,
        String type,
        Integer battery,
        String status,
        String onlineStatus,
        String controlMode,
        Long stateSeq,
        String missionStatus,
        String navigationStatus,
        Object controlOwner,
        Boolean estopActive,
        OffsetDateTime lastHeartbeatAt,
        List<RobotCameraResponse> cameras,
        List<Map<String, Object>> devices,
        String timestamp) {
}
