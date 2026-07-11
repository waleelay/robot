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
        name = "media_file",
        indexes = {
                @Index(name = "idx_file_org_robot_time", columnList = "orgId,robotId,createdAt"),
                @Index(name = "idx_file_org_extension", columnList = "orgId,extensionId"),
                @Index(name = "idx_file_type_status", columnList = "fileType,status"),
                @Index(name = "idx_file_robot_source", columnList = "robotId,sourceFileId")
        })
public class MediaFile {

    @Id
    @Column(length = 64)
    private String fileId;

    @Column(nullable = false, length = 64)
    private String orgId;

    @Column(length = 64)
    private String robotId;

    @Column(length = 64)
    private String deviceId;

    @Column(name = "extension_id", length = 128)
    private String extensionId;

    @Column(length = 256)
    private String sourceFileId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private FileType fileType;

    @Column(nullable = false, length = 256)
    private String fileName;

    @Column(nullable = false, length = 128)
    private String contentType;

    @Column(nullable = false)
    private long fileSize;

    @Column(nullable = false, length = 512)
    private String objectKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FileUploadMode uploadMode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private FileStatus status;

    @Column(columnDefinition = "json")
    private String metadataJson;

    @Column(length = 64)
    private String errorCode;

    @Column(length = 512)
    private String errorMessage;

    private OffsetDateTime uploadedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }
    public String getOrgId() { return orgId; }
    public void setOrgId(String orgId) { this.orgId = orgId; }
    public String getRobotId() { return robotId; }
    public void setRobotId(String robotId) { this.robotId = robotId; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getExtensionId() { return extensionId; }
    public void setExtensionId(String extensionId) { this.extensionId = extensionId; }
    public String getSourceFileId() { return sourceFileId; }
    public void setSourceFileId(String sourceFileId) { this.sourceFileId = sourceFileId; }
    public FileType getFileType() { return fileType; }
    public void setFileType(FileType fileType) { this.fileType = fileType; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }
    public String getObjectKey() { return objectKey; }
    public void setObjectKey(String objectKey) { this.objectKey = objectKey; }
    public FileUploadMode getUploadMode() { return uploadMode; }
    public void setUploadMode(FileUploadMode uploadMode) { this.uploadMode = uploadMode; }
    public FileStatus getStatus() { return status; }
    public void setStatus(FileStatus status) { this.status = status; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public OffsetDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(OffsetDateTime uploadedAt) { this.uploadedAt = uploadedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
