package com.robot.mediaserver.recording.dto;

import java.time.OffsetDateTime;

public record RecordingStatusResponse(
        String recordingId,
        String status,
        Long fileSize,
        Integer durationSeconds,
        boolean playable,
        String errorCode,
        String errorMessage,
        OffsetDateTime uploadedAt) {
}
