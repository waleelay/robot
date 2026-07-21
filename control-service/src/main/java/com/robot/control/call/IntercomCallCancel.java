package com.robot.control.call;

import java.time.OffsetDateTime;

/** MQTT payload sent when a robot cancels a ringing call. */
public record IntercomCallCancel(
        String callId,
        String robotId,
        String reason,
        OffsetDateTime timestamp) {
}
