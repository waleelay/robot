package com.robot.mediaserver.video.dto;

import java.time.OffsetDateTime;

public record SnapshotResponse(
        String snapshotId,
        String sessionId,
        String robotId,
        String deviceId,
        String channel,
        String quality,
        String status,
        String mode,
        boolean previewAccepted,
        String officialObjectKey,
        String previewObjectKey,
        String errorCode,
        String errorMessage,
        OffsetDateTime officialCapturedAt,
        OffsetDateTime createdAt) {
}
