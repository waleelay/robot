package com.robot.mediaserver.video.dto;

import java.util.List;

public record SnapshotListResponse(List<SnapshotResponse> items, int page, int pageSize, long total) {
}
