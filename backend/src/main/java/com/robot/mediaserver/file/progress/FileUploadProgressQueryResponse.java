package com.robot.mediaserver.file.progress;

import java.time.OffsetDateTime;
import java.util.List;

public record FileUploadProgressQueryResponse(
        List<FileUploadProgressItem> items,
        List<String> missingFileIds,
        OffsetDateTime generatedAt) {
}
