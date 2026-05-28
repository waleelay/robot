package com.robot.mediaserver.recording.dto;

import java.time.OffsetDateTime;

public record RecordingListItemResponse(
        String recordingId,
        String robotId,
        String deviceId,
        String fileName,
        long fileSize,
        Integer durationSeconds,
        OffsetDateTime recordedStartedAt,
        String status,
        String errorCode,
        OffsetDateTime uploadedAt) {
}
