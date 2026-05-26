package com.robot.mediaserver.robot.dto;

public record RobotCameraResponse(
        String cameraId,
        String deviceId,
        String name,
        String groupType,
        String channel,
        String quality,
        String status) {
}
