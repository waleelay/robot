package com.robot.mediaserver.file.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record FileUploadResponse(
        String fileId,
        String uploadId,
        String uploadMode,
        String status,
        long partSize,
        int partCount,
        List<FilePartInfoResponse> uploadedParts,
        List<FilePartUploadUrlResponse> partUrls,
        OffsetDateTime expiresAt) {
}
