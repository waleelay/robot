package com.robot.mediaserver.control.dto;

import java.time.OffsetDateTime;

public record VideoSessionResponse(
        String sessionId,
        String robotId,
        String deviceId,
        VideoChannel channel,
        VideoQuality quality,
        VideoSessionStatus status,
        String roomName,
        String livekitUrl,
        String viewerToken,
        String trackSid,
        String trackName,
        int viewerCount,
        IntercomStatus intercomStatus,
        boolean intercomAudioOnly,
        String intercomOperatorId,
        String robotAudioTrackSid,
        String robotAudioTrackName,
        String lastErrorCode,
        String lastErrorMessage,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
