package com.robot.mediaserver.recording.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record RecordingUploadResponse(
        String recordingId,
        String uploadId,
        String recordingStatus,
        boolean uploadRequired,
        long partSize,
        int partCount,
        List<PartInfoResponse> uploadedParts,
        OffsetDateTime expiresAt) {
}
