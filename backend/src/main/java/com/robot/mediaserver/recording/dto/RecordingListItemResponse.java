package com.robot.mediaserver.recording.dto;

import java.time.OffsetDateTime;

public record RecordingListItemResponse(
        String recordingId,
        String robotId,
        String deviceId,
        String sourceType,
        String fileName,
        long fileSize,
        Integer durationSeconds,
        OffsetDateTime recordedStartedAt,
        OffsetDateTime recordedEndedAt,
        String status,
        String errorCode,
        OffsetDateTime uploadedAt) {
}
