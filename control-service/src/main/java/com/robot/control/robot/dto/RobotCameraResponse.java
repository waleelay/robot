package com.robot.control.robot.dto;

public record RobotCameraResponse(
        String cameraId,
        String deviceId,
        String groupType,
        String name,
        String quality) {
}
