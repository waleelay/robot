package com.robot.mediaserver.file.dto;

import java.util.List;

public record BindTaskExecutionResponse(
        String taskExecutionId,
        List<FileListItemResponse> files) {
}
