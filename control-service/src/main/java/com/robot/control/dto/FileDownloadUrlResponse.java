package com.robot.control.dto;

import java.time.OffsetDateTime;

public record FileDownloadUrlResponse(String fileId, String downloadUrl, OffsetDateTime expiresAt) {
}
