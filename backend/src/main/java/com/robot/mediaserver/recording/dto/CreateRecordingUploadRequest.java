package com.robot.mediaserver.recording.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.time.OffsetDateTime;

public class CreateRecordingUploadRequest {

    @NotBlank
    private String sourceFileId;

    @NotBlank
    private String deviceId;

    @NotBlank
    private String fileName;

    @NotBlank
    private String contentType;

    @Positive
    private long fileSize;

    private String sha256;
    private OffsetDateTime recordedStartedAt;
    private Integer durationSeconds;

    public String getSourceFileId() { return sourceFileId; }
    public void setSourceFileId(String sourceFileId) { this.sourceFileId = sourceFileId; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
    public OffsetDateTime getRecordedStartedAt() { return recordedStartedAt; }
    public void setRecordedStartedAt(OffsetDateTime recordedStartedAt) { this.recordedStartedAt = recordedStartedAt; }
    public Integer getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }
}
