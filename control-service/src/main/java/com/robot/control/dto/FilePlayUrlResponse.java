package com.robot.control.dto;

import java.time.OffsetDateTime;

public record FilePlayUrlResponse(
        String fileId,
        String format,
        String contentType,
        String playUrl,
        OffsetDateTime expiresAt) {
}
