package com.robot.mediaserver.file.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "media_video_file")
public class MediaVideoFile {

    @Id
    @Column(length = 64)
    private String fileId;

    @Column(length = 32)
    private String videoCodec;

    @Column(length = 32)
    private String audioCodec;

    private Integer durationSeconds;
    private OffsetDateTime startedAt;
    private OffsetDateTime endedAt;
    private Integer width;
    private Integer height;

    @Column(length = 512)
    private String hlsPlaylistObjectKey;

    private Integer hlsSegmentCount;
    private Long hlsTotalSize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private VideoFileStatus status;

    @Column(length = 64)
    private String errorCode;

    @Column(length = 512)
    private String errorMessage;

    private OffsetDateTime processingStartedAt;
    private OffsetDateTime processingCompletedAt;

    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }
    public String getVideoCodec() { return videoCodec; }
    public void setVideoCodec(String videoCodec) { this.videoCodec = videoCodec; }
    public String getAudioCodec() { return audioCodec; }
    public void setAudioCodec(String audioCodec) { this.audioCodec = audioCodec; }
    public Integer getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }
    public OffsetDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(OffsetDateTime endedAt) { this.endedAt = endedAt; }
    public Integer getWidth() { return width; }
    public void setWidth(Integer width) { this.width = width; }
    public Integer getHeight() { return height; }
    public void setHeight(Integer height) { this.height = height; }
    public String getHlsPlaylistObjectKey() { return hlsPlaylistObjectKey; }
    public void setHlsPlaylistObjectKey(String hlsPlaylistObjectKey) { this.hlsPlaylistObjectKey = hlsPlaylistObjectKey; }
    public Integer getHlsSegmentCount() { return hlsSegmentCount; }
    public void setHlsSegmentCount(Integer hlsSegmentCount) { this.hlsSegmentCount = hlsSegmentCount; }
    public Long getHlsTotalSize() { return hlsTotalSize; }
    public void setHlsTotalSize(Long hlsTotalSize) { this.hlsTotalSize = hlsTotalSize; }
    public VideoFileStatus getStatus() { return status; }
    public void setStatus(VideoFileStatus status) { this.status = status; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public OffsetDateTime getProcessingStartedAt() { return processingStartedAt; }
    public void setProcessingStartedAt(OffsetDateTime processingStartedAt) { this.processingStartedAt = processingStartedAt; }
    public OffsetDateTime getProcessingCompletedAt() { return processingCompletedAt; }
    public void setProcessingCompletedAt(OffsetDateTime processingCompletedAt) { this.processingCompletedAt = processingCompletedAt; }
}
