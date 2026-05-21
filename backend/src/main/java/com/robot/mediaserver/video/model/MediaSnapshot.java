package com.robot.mediaserver.video.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * 媒体抓拍记录实体。
 *
 * <p>抓拍采用前端即时预览和服务端权威入库两段式设计。本实体记录服务端正式
 * 抓拍任务及其入库结果。</p>
 *
 * @author leelay
 * @date 2026/05/20
 */
@Entity
@Table(
        name = "media_snapshot",
        indexes = {
                @Index(name = "idx_snapshot_session_time", columnList = "sessionId,createdAt"),
                @Index(name = "idx_snapshot_device_time", columnList = "robotId,deviceId,createdAt")
        })
public class MediaSnapshot {

    @Id
    @Column(length = 64)
    private String snapshotId;

    @Column(nullable = false, length = 64)
    private String sessionId;

    @Column(length = 128)
    private String trackSid;

    @Column(nullable = false, length = 64)
    private String robotId;

    @Column(nullable = false, length = 64)
    private String deviceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private VideoChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private VideoQuality quality;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private SnapshotStatus status;

    @Column(length = 512)
    private String officialObjectKey;

    @Column(length = 512)
    private String previewObjectKey;

    @Column(length = 64)
    private String previewImageHash;

    @Column(length = 64)
    private String source;

    private Long timeDeltaMs;

    @Column(length = 64)
    private String reason;

    @Column(length = 512)
    private String remark;

    @Column(length = 64)
    private String createdBy;

    private OffsetDateTime clientCapturedAt;
    private OffsetDateTime officialCapturedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    @Column(length = 64)
    private String errorCode;

    @Column(length = 512)
    private String errorMessage;

    public String getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(String snapshotId) {
        this.snapshotId = snapshotId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getTrackSid() {
        return trackSid;
    }

    public void setTrackSid(String trackSid) {
        this.trackSid = trackSid;
    }

    public String getRobotId() {
        return robotId;
    }

    public void setRobotId(String robotId) {
        this.robotId = robotId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public VideoChannel getChannel() {
        return channel;
    }

    public void setChannel(VideoChannel channel) {
        this.channel = channel;
    }

    public VideoQuality getQuality() {
        return quality;
    }

    public void setQuality(VideoQuality quality) {
        this.quality = quality;
    }

    public SnapshotStatus getStatus() {
        return status;
    }

    public void setStatus(SnapshotStatus status) {
        this.status = status;
    }

    public String getOfficialObjectKey() {
        return officialObjectKey;
    }

    public void setOfficialObjectKey(String officialObjectKey) {
        this.officialObjectKey = officialObjectKey;
    }

    public String getPreviewObjectKey() {
        return previewObjectKey;
    }

    public void setPreviewObjectKey(String previewObjectKey) {
        this.previewObjectKey = previewObjectKey;
    }

    public String getPreviewImageHash() {
        return previewImageHash;
    }

    public void setPreviewImageHash(String previewImageHash) {
        this.previewImageHash = previewImageHash;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Long getTimeDeltaMs() {
        return timeDeltaMs;
    }

    public void setTimeDeltaMs(Long timeDeltaMs) {
        this.timeDeltaMs = timeDeltaMs;
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

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public OffsetDateTime getClientCapturedAt() {
        return clientCapturedAt;
    }

    public void setClientCapturedAt(OffsetDateTime clientCapturedAt) {
        this.clientCapturedAt = clientCapturedAt;
    }

    public OffsetDateTime getOfficialCapturedAt() {
        return officialCapturedAt;
    }

    public void setOfficialCapturedAt(OffsetDateTime officialCapturedAt) {
        this.officialCapturedAt = officialCapturedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
