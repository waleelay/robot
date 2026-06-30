package com.robot.mediaserver.file.dto;

import java.time.OffsetDateTime;

public record FileStatusResponse(
        String fileId,
        String status,
        long fileSize,
        boolean ready,
        String errorCode,
        String errorMessage,
        OffsetDateTime uploadedAt) {
}
