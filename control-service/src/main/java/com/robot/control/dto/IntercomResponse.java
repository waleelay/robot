package com.robot.control.dto;

import java.time.OffsetDateTime;

public record IntercomResponse(
        String sessionId,
        String robotId,
        String deviceId,
        String roomName,
        VideoSessionStatus videoStatus,
        IntercomStatus intercomStatus,
        boolean intercomAudioOnly,
        String livekitUrl,
        String operatorToken,
        OffsetDateTime expiresAt) {
}
