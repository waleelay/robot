package com.robot.control.robot.dto;

/**
 * 机器人摄像头信息响应。
 *
 * @author leelay
 * @date 2026-07-05
 *
 * @param cameraId 摄像头 ID
 * @param deviceId 设备 ID
 * @param groupType 摄像头分组类型
 * @param name 名称
 * @param quality 视频清晰度
 */
public record RobotCameraResponse(
        String cameraId,
        String deviceId,
        String groupType,
        String name,
        String quality) {
}
