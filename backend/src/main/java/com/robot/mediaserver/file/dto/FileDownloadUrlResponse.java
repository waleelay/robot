package com.robot.mediaserver.file.dto;

import java.time.OffsetDateTime;

public record FileDownloadUrlResponse(String fileId, String downloadUrl, OffsetDateTime expiresAt) {
}
