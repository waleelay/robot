package com.robot.mediaserver.video.dto;

import java.time.OffsetDateTime;

/**
 * 抓拍任务响应。
 *
 * @author leelay
 * @date 2026/05/19
 */
public record SnapshotResponse(
        String snapshotId,
        String status,
        String mode,
        boolean previewAccepted,
        String officialObjectKey,
        String previewObjectKey,
        String errorCode,
        String errorMessage,
        OffsetDateTime officialCapturedAt,
        OffsetDateTime createdAt) {
}
