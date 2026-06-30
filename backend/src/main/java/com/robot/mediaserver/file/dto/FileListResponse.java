package com.robot.mediaserver.file.dto;

import java.util.List;

public record FileListResponse(List<FileListItemResponse> items, int page, int size, long total) {
}
