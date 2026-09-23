package com.robot.mediaserver.file.model;

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
        name = "media_file_upload",
        indexes = {
                @Index(name = "idx_file_upload_file", columnList = "fileId"),
                @Index(name = "idx_file_upload_status_expire", columnList = "status,expiresAt"),
                @Index(name = "uk_file_upload_storage", columnList = "storageUploadId", unique = true)
        })
public class MediaFileUpload {

    @Id
    @Column(length = 64)
    private String uploadId;

    @Column(nullable = false, length = 64)
    private String fileId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FileUploadMode uploadMode;

    @Column(length = 256)
    private String storageUploadId;

    private Long partSize;
    private Integer partCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FileUploadStatus status;

    private OffsetDateTime expiresAt;
    private OffsetDateTime lastActiveAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime completedAt;

    @Column(length = 64)
    private String completionOwner;

    private OffsetDateTime completionLeaseExpiresAt;

    public String getUploadId() { return uploadId; }
    public void setUploadId(String uploadId) { this.uploadId = uploadId; }
    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }
    public FileUploadMode getUploadMode() { return uploadMode; }
    public void setUploadMode(FileUploadMode uploadMode) { this.uploadMode = uploadMode; }
    public String getStorageUploadId() { return storageUploadId; }
    public void setStorageUploadId(String storageUploadId) { this.storageUploadId = storageUploadId; }
    public Long getPartSize() { return partSize; }
    public void setPartSize(Long partSize) { this.partSize = partSize; }
    public Integer getPartCount() { return partCount; }
    public void setPartCount(Integer partCount) { this.partCount = partCount; }
    public FileUploadStatus getStatus() { return status; }
    public void setStatus(FileUploadStatus status) { this.status = status; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }
    public OffsetDateTime getLastActiveAt() { return lastActiveAt; }
    public void setLastActiveAt(OffsetDateTime lastActiveAt) { this.lastActiveAt = lastActiveAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }
    public String getCompletionOwner() { return completionOwner; }
    public void setCompletionOwner(String completionOwner) { this.completionOwner = completionOwner; }
    public OffsetDateTime getCompletionLeaseExpiresAt() { return completionLeaseExpiresAt; }
    public void setCompletionLeaseExpiresAt(OffsetDateTime completionLeaseExpiresAt) { this.completionLeaseExpiresAt = completionLeaseExpiresAt; }
}
