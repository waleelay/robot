package com.robot.control.mileage;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 从一条边缘设备状态中提取出的里程读数。
 *
 * @param robotId 机器人 ID
 * @param messageId 上游消息的唯一标识，用于去重和关联
 * @param eventTime 设备读数对应的事件时间
 * @param totalMileageMeters 设备全生命周期累计里程读数，单位米；未上报时为空
 * @param currentMileageMeters 设备本轮累计里程读数，单位米；未上报时为空
 */
public record MileageReading(
        String robotId,
        String messageId,
        OffsetDateTime eventTime,
        BigDecimal totalMileageMeters,
        BigDecimal currentMileageMeters) {
}
