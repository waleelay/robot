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
 * @param typeCode 机器人类型编码
 * @param battery 电量
 * @param status 状态
 * @param statusChangedAt 在线状态最后变更时间
 * @param controlMode 控制模式
 * @param controlModeName 控制模式中文名称
 * @param stateSeq 状态序号
 * @param missionStatus 任务状态
 * @param navigationStatus 导航状态
 * @param controlOwner 控制占用者
 * @param estopActive 急停状态
 * @param lastHeartbeatAt 最后心跳时间
 * @param cameras 摄像头列表
 * @param devices 设备能力列表
 * @param healthStatus 健康状态
 * @param timestamp 状态时间
 * @param speed 最后上报速度（米/秒），未上报为 null
 * @param runtimeUpdatedAt 最后接受边缘状态的服务端时间；不随媒体心跳或离线扫描变化
 */
public record RobotDeviceResponse(
        String robotId,
        String clientId,
        String name,
        String type,
        String typeCode,
        Integer battery,
        String status,
        String statusChangedAt,
        String controlMode,
        String controlModeName,
        Long stateSeq,
        String missionStatus,
        String navigationStatus,
        Object controlOwner,
        Boolean estopActive,
        OffsetDateTime lastHeartbeatAt,
        List<RobotCameraResponse> cameras,
        List<Map<String, Object>> devices,
        String healthStatus,
        String timestamp,
        Double speed,
        String runtimeUpdatedAt) {
}
