package com.robot.control.dto;

import java.time.OffsetDateTime;

public record IntercomStartCommand(
        String commandId,
        String sessionId,
        String robotId,
        String deviceId,
        String roomName,
        String livekitUrl,
        String robotToken,
        boolean publishAudio,
        boolean subscribeOperatorAudio,
        boolean publishVideo,
        OffsetDateTime expiresAt) {
}
