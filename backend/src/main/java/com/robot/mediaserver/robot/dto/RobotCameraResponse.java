package com.robot.mediaserver.robot.dto;

public record RobotCameraResponse(
        String cameraId,
        String deviceId,
        String groupType,
        String name,
        String quality) {
}
