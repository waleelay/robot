package com.robot.control.dto;

import java.time.OffsetDateTime;

public record VideoStartCommand(
        String commandId,
        String sessionId,
        String robotId,
        String deviceId,
        VideoChannel channel,
        VideoQuality quality,
        String livekitUrl,
        String roomName,
        String publisherToken,
        String publishIdentity,
        OffsetDateTime expiresAt) {
}
