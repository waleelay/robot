package com.robot.control.call;

import java.time.OffsetDateTime;

/** MQTT payload sent when a robot asks the control center to start an intercom call. */
public record IntercomCallInvite(
        String callId,
        String robotId,
        String deviceId,
        String channel,
        String quality,
        String reason,
        Integer timeoutSeconds,
        OffsetDateTime timestamp) {
}
