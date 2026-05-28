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
        name = "media_recording_upload",
        indexes = {
                @Index(name = "idx_recording_upload_recording", columnList = "recordingId"),
                @Index(name = "idx_recording_upload_status_expire", columnList = "status,expiresAt")
        })
public class MediaRecordingUpload {

    @Id
    @Column(length = 64)
    private String uploadId;

    @Column(nullable = false, length = 64)
    private String recordingId;

    @Column(nullable = false, length = 256)
    private String storageUploadId;

    @Column(nullable = false)
    private long partSize;

    @Column(nullable = false)
    private int partCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UploadStatus status;

    private OffsetDateTime expiresAt;
    private OffsetDateTime lastActiveAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime completedAt;

    public String getUploadId() { return uploadId; }
    public void setUploadId(String uploadId) { this.uploadId = uploadId; }
    public String getRecordingId() { return recordingId; }
    public void setRecordingId(String recordingId) { this.recordingId = recordingId; }
    public String getStorageUploadId() { return storageUploadId; }
    public void setStorageUploadId(String storageUploadId) { this.storageUploadId = storageUploadId; }
    public long getPartSize() { return partSize; }
    public void setPartSize(long partSize) { this.partSize = partSize; }
    public int getPartCount() { return partCount; }
    public void setPartCount(int partCount) { this.partCount = partCount; }
    public UploadStatus getStatus() { return status; }
    public void setStatus(UploadStatus status) { this.status = status; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }
    public OffsetDateTime getLastActiveAt() { return lastActiveAt; }
    public void setLastActiveAt(OffsetDateTime lastActiveAt) { this.lastActiveAt = lastActiveAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }
}
