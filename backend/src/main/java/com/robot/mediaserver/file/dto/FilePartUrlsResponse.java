package com.robot.mediaserver.file.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record FilePartUrlsResponse(OffsetDateTime expiresAt, List<FilePartUploadUrlResponse> parts) {
}
