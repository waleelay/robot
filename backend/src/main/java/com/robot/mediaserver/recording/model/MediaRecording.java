package com.robot.mediaserver.recording.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(
        name = "media_recording",
        indexes = {
                @Index(name = "uk_recording_robot_source", columnList = "robotId,sourceFileId", unique = true),
                @Index(name = "idx_recording_org_robot_time", columnList = "orgId,robotId,recordedStartedAt"),
                @Index(name = "idx_recording_org_device_time", columnList = "orgId,deviceId,recordedStartedAt"),
                @Index(name = "idx_recording_status_update", columnList = "status,updatedAt")
        })
public class MediaRecording {

    @Id
    @Column(length = 64)
    private String recordingId;

    @Column(nullable = false, length = 64)
    private String orgId;

    @Column(nullable = false, length = 64)
    private String robotId;

    @Column(nullable = false, length = 64)
    private String deviceId;

    @Column(nullable = false, length = 128)
    private String sourceFileId;

    @Column(nullable = false, length = 256)
    private String fileName;

    @Column(nullable = false, length = 64)
    private String sourceContentType;

    @Column(length = 32)
    private String videoCodec;

    @Column(length = 32)
    private String audioCodec;

    @Column(nullable = false)
    private long fileSize;

    @Column(length = 64)
    private String sha256;

    private OffsetDateTime recordedStartedAt;
    private Integer reportedDurationSeconds;
    private Integer durationSeconds;

    @Column(nullable = false, length = 512)
    private String sourceObjectKey;

    @Column(length = 512)
    private String hlsPlaylistObjectKey;

    private Integer hlsSegmentCount;
    private Long hlsTotalSize;

    @Column(nullable = false, length = 16)
    private String playbackFormat = "HLS";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private RecordingStatus status;

    @Column(length = 64)
    private String errorCode;

    @Column(length = 512)
    private String errorMessage;

    private OffsetDateTime uploadedAt;
    private OffsetDateTime processingStartedAt;
    private OffsetDateTime processingCompletedAt;
    private OffsetDateTime processingLeaseUntil;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public String getRecordingId() { return recordingId; }
    public void setRecordingId(String recordingId) { this.recordingId = recordingId; }
    public String getOrgId() { return orgId; }
    public void setOrgId(String orgId) { this.orgId = orgId; }
    public String getRobotId() { return robotId; }
    public void setRobotId(String robotId) { this.robotId = robotId; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getSourceFileId() { return sourceFileId; }
    public void setSourceFileId(String sourceFileId) { this.sourceFileId = sourceFileId; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getSourceContentType() { return sourceContentType; }
    public void setSourceContentType(String sourceContentType) { this.sourceContentType = sourceContentType; }
    public String getVideoCodec() { return videoCodec; }
    public void setVideoCodec(String videoCodec) { this.videoCodec = videoCodec; }
    public String getAudioCodec() { return audioCodec; }
    public void setAudioCodec(String audioCodec) { this.audioCodec = audioCodec; }
    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
    public OffsetDateTime getRecordedStartedAt() { return recordedStartedAt; }
    public void setRecordedStartedAt(OffsetDateTime recordedStartedAt) { this.recordedStartedAt = recordedStartedAt; }
    public Integer getReportedDurationSeconds() { return reportedDurationSeconds; }
    public void setReportedDurationSeconds(Integer reportedDurationSeconds) { this.reportedDurationSeconds = reportedDurationSeconds; }
    public Integer getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }
    public String getSourceObjectKey() { return sourceObjectKey; }
    public void setSourceObjectKey(String sourceObjectKey) { this.sourceObjectKey = sourceObjectKey; }
    public String getHlsPlaylistObjectKey() { return hlsPlaylistObjectKey; }
    public void setHlsPlaylistObjectKey(String hlsPlaylistObjectKey) { this.hlsPlaylistObjectKey = hlsPlaylistObjectKey; }
    public Integer getHlsSegmentCount() { return hlsSegmentCount; }
    public void setHlsSegmentCount(Integer hlsSegmentCount) { this.hlsSegmentCount = hlsSegmentCount; }
    public Long getHlsTotalSize() { return hlsTotalSize; }
    public void setHlsTotalSize(Long hlsTotalSize) { this.hlsTotalSize = hlsTotalSize; }
    public String getPlaybackFormat() { return playbackFormat; }
    public void setPlaybackFormat(String playbackFormat) { this.playbackFormat = playbackFormat; }
    public RecordingStatus getStatus() { return status; }
    public void setStatus(RecordingStatus status) { this.status = status; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public OffsetDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(OffsetDateTime uploadedAt) { this.uploadedAt = uploadedAt; }
    public OffsetDateTime getProcessingStartedAt() { return processingStartedAt; }
    public void setProcessingStartedAt(OffsetDateTime processingStartedAt) { this.processingStartedAt = processingStartedAt; }
    public OffsetDateTime getProcessingCompletedAt() { return processingCompletedAt; }
    public void setProcessingCompletedAt(OffsetDateTime processingCompletedAt) { this.processingCompletedAt = processingCompletedAt; }
    public OffsetDateTime getProcessingLeaseUntil() { return processingLeaseUntil; }
    public void setProcessingLeaseUntil(OffsetDateTime processingLeaseUntil) { this.processingLeaseUntil = processingLeaseUntil; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
