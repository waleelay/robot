package com.robot.control.robot.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 机器人设备在线状态响应。
 *
 * @author leelay
 * @date 2026-07-05
 *
 * @param robotId 机器人 ID
 * @param clientId 客户端 ID
 * @param name 名称
 * @param type 机器人类型
 * @param battery 电量
 * @param status 状态
 * @param controlMode 控制模式
 * @param stateSeq 状态序号
 * @param missionStatus 任务状态
 * @param navigationStatus 导航状态
 * @param controlOwner 控制占用者
 * @param estopActive 急停状态
 * @param lastHeartbeatAt 最后心跳时间
 * @param cameras 摄像头列表
 * @param devices 设备能力列表
 * @param timestamp 状态时间
 */
public record RobotDeviceResponse(
        String robotId,
        String clientId,
        String name,
        String type,
        Integer battery,
        String status,
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
