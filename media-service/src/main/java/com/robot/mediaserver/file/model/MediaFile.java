package com.robot.mediaserver.file.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import com.robot.media.common.file.FileStatus;
import com.robot.media.common.file.FileType;


/** 文件主记录，保存租户、来源、所有者、对象位置与处理状态；对象内容存放在 MinIO。 */
@Entity
@Table(
        name = "media_file",
        indexes = {
                @Index(name = "idx_file_org_robot_time", columnList = "orgId,robotId,createdAt"),
                @Index(name = "idx_file_org_creator_time", columnList = "orgId,createdBy,createdAt"),
                @Index(name = "idx_file_org_extension", columnList = "orgId,extensionId"),
                @Index(name = "idx_file_type_status", columnList = "fileType,status"),
                @Index(name = "uk_file_robot_source", columnList = "robotId,sourceFileId", unique = true)
        })
public class MediaFile {

    /**
     * 文件 ID。
     */
    @Id
    @Column(length = 64)
    private String fileId;

    /**
     * 组织 ID。
     */
    @Column(nullable = false, length = 64)
    private String orgId;

    /**
     * 创建该记录的用户标识。
     */
    @Column(length = 64)
    private String createdBy;

    /**
     * 机器人 ID。
     */
    @Column(length = 64)
    private String robotId;

    /**
     * 设备 ID。
     */
    @Column(length = 64)
    private String deviceId;

    /**
     * 通用扩展 ID。
     */
    @Column(name = "extension_id", length = 128)
    private String extensionId;

    /**
     * 源文件 ID。
     */
    @Column(length = 256)
    private String sourceFileId;

    /**
     * 文件类型。
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private FileType fileType;

    /**
     * 原始文件名。
     */
    @Column(nullable = false, length = 256)
    private String fileName;

    /**
     * 文件媒体类型。
     */
    @Column(nullable = false, length = 128)
    private String contentType;

    /**
     * 文件字节数。
     */
    @Column(nullable = false)
    private long fileSize;

    /**
     * 对象在存储桶内的键，不包含访问凭据。
     */
    @Column(nullable = false, length = 512)
    private String objectKey;

    /**
     * 上传方式。
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FileUploadMode uploadMode;

    /**
     * 通用文件生命周期状态；READY 表示文件可用，视频还需结合后处理状态判断播放就绪。
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private FileStatus status;

    /**
     * 按既有协议保存的文件扩展元数据 JSON。
     */
    @Column(columnDefinition = "json")
    private String metadataJson;

    /**
     * 错误码。
     */
    @Column(length = 64)
    private String errorCode;

    /**
     * 失败原因；无错误时为空。
     */
    @Column(length = 512)
    private String errorMessage;

    /**
     * 上传完成时间；未完成时为空。
     */
    private OffsetDateTime uploadedAt;
    /**
     * 创建时间。
     */
    private OffsetDateTime createdAt;
    /**
     * 更新时间。
     */
    private OffsetDateTime updatedAt;

    /**
     * 读取{@link #fileId}。
     *
     * @return 当前值，含义与约束见{@link #fileId}
     */
    public String getFileId() { return fileId; }
    /**
     * 更新{@link #fileId}。
     *
     * @param fileId 新值，含义与约束见{@link #fileId}
     */
    public void setFileId(String fileId) { this.fileId = fileId; }
    /**
     * 读取{@link #orgId}。
     *
     * @return 当前值，含义与约束见{@link #orgId}
     */
    public String getOrgId() { return orgId; }
    /**
     * 更新{@link #orgId}。
     *
     * @param orgId 新值，含义与约束见{@link #orgId}
     */
    public void setOrgId(String orgId) { this.orgId = orgId; }
    /**
     * 读取{@link #createdBy}。
     *
     * @return 当前值，含义与约束见{@link #createdBy}
     */
    public String getCreatedBy() { return createdBy; }
    /**
     * 更新{@link #createdBy}。
     *
     * @param createdBy 新值，含义与约束见{@link #createdBy}
     */
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    /**
     * 读取{@link #robotId}。
     *
     * @return 当前值，含义与约束见{@link #robotId}
     */
    public String getRobotId() { return robotId; }
    /**
     * 更新{@link #robotId}。
     *
     * @param robotId 新值，含义与约束见{@link #robotId}
     */
    public void setRobotId(String robotId) { this.robotId = robotId; }
    /**
     * 读取{@link #deviceId}。
     *
     * @return 当前值，含义与约束见{@link #deviceId}
     */
    public String getDeviceId() { return deviceId; }
    /**
     * 更新{@link #deviceId}。
     *
     * @param deviceId 新值，含义与约束见{@link #deviceId}
     */
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    /**
     * 读取{@link #extensionId}。
     *
     * @return 当前值，含义与约束见{@link #extensionId}
     */
    public String getExtensionId() { return extensionId; }
    /**
     * 更新{@link #extensionId}。
     *
     * @param extensionId 新值，含义与约束见{@link #extensionId}
     */
    public void setExtensionId(String extensionId) { this.extensionId = extensionId; }
    /**
     * 读取{@link #sourceFileId}。
     *
     * @return 当前值，含义与约束见{@link #sourceFileId}
     */
    public String getSourceFileId() { return sourceFileId; }
    /**
     * 更新{@link #sourceFileId}。
     *
     * @param sourceFileId 新值，含义与约束见{@link #sourceFileId}
     */
    public void setSourceFileId(String sourceFileId) { this.sourceFileId = sourceFileId; }
    /**
     * 读取{@link #fileType}。
     *
     * @return 当前值，含义与约束见{@link #fileType}
     */
    public FileType getFileType() { return fileType; }
    /**
     * 更新{@link #fileType}。
     *
     * @param fileType 新值，含义与约束见{@link #fileType}
     */
    public void setFileType(FileType fileType) { this.fileType = fileType; }
    /**
     * 读取{@link #fileName}。
     *
     * @return 当前值，含义与约束见{@link #fileName}
     */
    public String getFileName() { return fileName; }
    /**
     * 更新{@link #fileName}。
     *
     * @param fileName 新值，含义与约束见{@link #fileName}
     */
    public void setFileName(String fileName) { this.fileName = fileName; }
    /**
     * 读取{@link #contentType}。
     *
     * @return 当前值，含义与约束见{@link #contentType}
     */
    public String getContentType() { return contentType; }
    /**
     * 更新{@link #contentType}。
     *
     * @param contentType 新值，含义与约束见{@link #contentType}
     */
    public void setContentType(String contentType) { this.contentType = contentType; }
    /**
     * 读取{@link #fileSize}。
     *
     * @return 当前值，含义与约束见{@link #fileSize}
     */
    public long getFileSize() { return fileSize; }
    /**
     * 更新{@link #fileSize}。
     *
     * @param fileSize 新值，含义与约束见{@link #fileSize}
     */
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }
    /**
     * 读取{@link #objectKey}。
     *
     * @return 当前值，含义与约束见{@link #objectKey}
     */
    public String getObjectKey() { return objectKey; }
    /**
     * 更新{@link #objectKey}。
     *
     * @param objectKey 新值，含义与约束见{@link #objectKey}
     */
    public void setObjectKey(String objectKey) { this.objectKey = objectKey; }
    /**
     * 读取{@link #uploadMode}。
     *
     * @return 当前值，含义与约束见{@link #uploadMode}
     */
    public FileUploadMode getUploadMode() { return uploadMode; }
    /**
     * 更新{@link #uploadMode}。
     *
     * @param uploadMode 新值，含义与约束见{@link #uploadMode}
     */
    public void setUploadMode(FileUploadMode uploadMode) { this.uploadMode = uploadMode; }
    /**
     * 读取{@link #status}。
     *
     * @return 当前值，含义与约束见{@link #status}
     */
    public FileStatus getStatus() { return status; }
    /**
     * 更新{@link #status}。
     *
     * @param status 新值，含义与约束见{@link #status}
     */
    public void setStatus(FileStatus status) { this.status = status; }
    /**
     * 读取{@link #metadataJson}。
     *
     * @return 当前值，含义与约束见{@link #metadataJson}
     */
    public String getMetadataJson() { return metadataJson; }
    /**
     * 更新{@link #metadataJson}。
     *
     * @param metadataJson 新值，含义与约束见{@link #metadataJson}
     */
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    /**
     * 读取{@link #errorCode}。
     *
     * @return 当前值，含义与约束见{@link #errorCode}
     */
    public String getErrorCode() { return errorCode; }
    /**
     * 更新{@link #errorCode}。
     *
     * @param errorCode 新值，含义与约束见{@link #errorCode}
     */
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    /**
     * 读取{@link #errorMessage}。
     *
     * @return 当前值，含义与约束见{@link #errorMessage}
     */
    public String getErrorMessage() { return errorMessage; }
    /**
     * 更新{@link #errorMessage}。
     *
     * @param errorMessage 新值，含义与约束见{@link #errorMessage}
     */
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    /**
     * 读取{@link #uploadedAt}。
     *
     * @return 当前值，含义与约束见{@link #uploadedAt}
     */
    public OffsetDateTime getUploadedAt() { return uploadedAt; }
    /**
     * 更新{@link #uploadedAt}。
     *
     * @param uploadedAt 新值，含义与约束见{@link #uploadedAt}
     */
    public void setUploadedAt(OffsetDateTime uploadedAt) { this.uploadedAt = uploadedAt; }
    /**
     * 读取{@link #createdAt}。
     *
     * @return 当前值，含义与约束见{@link #createdAt}
     */
    public OffsetDateTime getCreatedAt() { return createdAt; }
    /**
     * 更新{@link #createdAt}。
     *
     * @param createdAt 新值，含义与约束见{@link #createdAt}
     */
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    /**
     * 读取{@link #updatedAt}。
     *
     * @return 当前值，含义与约束见{@link #updatedAt}
     */
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    /**
     * 更新{@link #updatedAt}。
     *
     * @param updatedAt 新值，含义与约束见{@link #updatedAt}
     */
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
