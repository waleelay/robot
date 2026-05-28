package com.robot.mediaserver.recording.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record PartUrlsResponse(OffsetDateTime expiresAt, List<PartUploadUrlResponse> parts) {
}
