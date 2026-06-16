package com.robot.mediaserver.video.dto;

import java.time.OffsetDateTime;

/**
 * 创建抓拍任务请求。
 *
 * @author leelay
 * @date 2026/05/19
 */
public class CreateSnapshotRequest {

    private String trackSid;

    private String reason = "manual_abnormal";
    private String remark;
    private OffsetDateTime clientCapturedAt;
    private String clientPreviewObjectKey;
    private String previewImageHash;

    public String getTrackSid() {
        return trackSid;
    }

    public void setTrackSid(String trackSid) {
        this.trackSid = trackSid;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public OffsetDateTime getClientCapturedAt() {
        return clientCapturedAt;
    }

    public void setClientCapturedAt(OffsetDateTime clientCapturedAt) {
        this.clientCapturedAt = clientCapturedAt;
    }

    public String getClientPreviewObjectKey() {
        return clientPreviewObjectKey;
    }

    public void setClientPreviewObjectKey(String clientPreviewObjectKey) {
        this.clientPreviewObjectKey = clientPreviewObjectKey;
    }

    public String getPreviewImageHash() {
        return previewImageHash;
    }

    public void setPreviewImageHash(String previewImageHash) {
        this.previewImageHash = previewImageHash;
    }
}
