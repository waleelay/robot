package com.robot.control.mileage;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 从一条边缘设备状态中提取出的里程读数。 */
public record MileageReading(
        String robotId,
        String messageId,
        OffsetDateTime eventTime,
        BigDecimal totalMileageMeters,
        BigDecimal currentMileageMeters) {
}
