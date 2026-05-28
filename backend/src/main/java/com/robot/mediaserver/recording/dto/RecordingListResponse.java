package com.robot.mediaserver.recording.dto;

import java.util.List;

public record RecordingListResponse(List<RecordingListItemResponse> items, int page, int size, long total) {
}
