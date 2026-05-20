package com.robot.mediaserver.video.dto;

import java.time.OffsetDateTime;

/**
 * 完成抓拍任务请求。
 *
 * @author leelay
 * @date 2026/05/20
 */
public class CompleteSnapshotRequest {

    private String officialObjectKey;
    private OffsetDateTime officialCapturedAt;
    private Long timeDeltaMs;

    public String getOfficialObjectKey() {
        return officialObjectKey;
    }

    public void setOfficialObjectKey(String officialObjectKey) {
        this.officialObjectKey = officialObjectKey;
    }

    public OffsetDateTime getOfficialCapturedAt() {
        return officialCapturedAt;
    }

    public void setOfficialCapturedAt(OffsetDateTime officialCapturedAt) {
        this.officialCapturedAt = officialCapturedAt;
    }

    public Long getTimeDeltaMs() {
        return timeDeltaMs;
    }

    public void setTimeDeltaMs(Long timeDeltaMs) {
        this.timeDeltaMs = timeDeltaMs;
    }
}
