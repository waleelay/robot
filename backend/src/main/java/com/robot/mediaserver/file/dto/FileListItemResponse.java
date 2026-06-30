package com.robot.mediaserver.file.dto;

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
        Integer width,
        Integer height,
        String status,
        String videoStatus,
        String errorCode,
        OffsetDateTime uploadedAt,
        OffsetDateTime createdAt,
        String metadata) {
}
