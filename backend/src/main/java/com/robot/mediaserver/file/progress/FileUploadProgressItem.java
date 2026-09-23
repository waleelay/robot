package com.robot.mediaserver.file.progress;

import java.time.OffsetDateTime;

public record FileUploadProgressItem(
        String fileId,
        String uploadId,
        String fileName,
        String fileType,
        FileProgressPhase phase,
        long uploadedBytes,
        long totalBytes,
        int uploadedPartCount,
        Integer partCount,
        double uploadPercent,
        boolean ready,
        long version,
        OffsetDateTime updatedAt,
        String errorCode,
        String errorMessage) {
}
