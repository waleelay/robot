package com.robot.mediaserver.control.dto;

import java.time.OffsetDateTime;

public record FileListItemResponse(
        String fileId,
        String robotId,
        String deviceId,
        String taskExecutionId,
        String fileType,
        String fileName,
        String contentType,
        long fileSize,
        Integer durationSeconds,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt,
        Integer width,
        Integer height,
        String status,
        String videoStatus,
        String errorCode,
        OffsetDateTime uploadedAt,
        OffsetDateTime createdAt,
        String metadata) {
}
