package com.robot.mediaserver.file.dto;

import java.util.List;

public record FileBatchDeleteResponse(
        int total,
        int succeeded,
        int failed,
        List<FileDeleteResultResponse> results) {
}
