package com.robot.mediaserver.file.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** 分片上传会话，保存存储会话标识、分片规划、过期时间和合并操作租约。 */
@Entity
@Table(
        name = "media_file_upload",
        indexes = {
                @Index(name = "idx_file_upload_file", columnList = "fileId"),
                @Index(name = "idx_file_upload_status_expire", columnList = "status,expiresAt"),
                @Index(name = "uk_file_upload_storage", columnList = "storageUploadId", unique = true)
        })
public class MediaFileUpload {

    /**
     * 平台上传会话唯一标识，完成后仍保留
     */
    @Id
    @Column(length = 64)
    private String uploadId;

    /**
     * 文件 ID。
     */
    @Column(nullable = false, length = 64)
    private String fileId;

    /**
     * 上传方式。
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FileUploadMode uploadMode;

    /**
     * 平台生成的暂存分片会话标识，用于组织分片对象键。
     */
    @Column(length = 256)
    private String storageUploadId;

    /**
     * 本上传会话计划的单个分片字节数，完成后仍保留
     */
    private Long partSize;
    /**
     * 本上传会话计划的分片总数，完成后仍保留
     */
    private Integer partCount;

    /**
     * 分片上传会话状态；COMPLETING 表示合并流程已领取，尚不代表全部分片校验通过。
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FileUploadStatus status;

    /**
     * 有效期截止时间。
     */
    private OffsetDateTime expiresAt;
    /**
     * 最近一次有效活动时间。
     */
    private OffsetDateTime lastActiveAt;
    /**
     * 创建时间。
     */
    private OffsetDateTime createdAt;
    /**
     * 业务完成时间，尚未完成时为空。
     */
    private OffsetDateTime completedAt;

    /**
     * 当前分片合并处理权的持有者标识。
     */
    @Column(length = 64)
    private String completionOwner;

    /**
     * 当前分片合并处理权的到期时间。
     */
    private OffsetDateTime completionLeaseExpiresAt;

    /**
     * 读取{@link #uploadId}。
     *
     * @return 当前值，含义与约束见{@link #uploadId}
     */
    public String getUploadId() { return uploadId; }
    /**
     * 更新{@link #uploadId}。
     *
     * @param uploadId 新值，含义与约束见{@link #uploadId}
     */
    public void setUploadId(String uploadId) { this.uploadId = uploadId; }
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
     * 读取{@link #storageUploadId}。
     *
     * @return 当前值，含义与约束见{@link #storageUploadId}
     */
    public String getStorageUploadId() { return storageUploadId; }
    /**
     * 更新{@link #storageUploadId}。
     *
     * @param storageUploadId 新值，含义与约束见{@link #storageUploadId}
     */
    public void setStorageUploadId(String storageUploadId) { this.storageUploadId = storageUploadId; }
    /**
     * 读取{@link #partSize}。
     *
     * @return 当前值，含义与约束见{@link #partSize}
     */
    public Long getPartSize() { return partSize; }
    /**
     * 更新{@link #partSize}。
     *
     * @param partSize 新值，含义与约束见{@link #partSize}
     */
    public void setPartSize(Long partSize) { this.partSize = partSize; }
    /**
     * 读取{@link #partCount}。
     *
     * @return 当前值，含义与约束见{@link #partCount}
     */
    public Integer getPartCount() { return partCount; }
    /**
     * 更新{@link #partCount}。
     *
     * @param partCount 新值，含义与约束见{@link #partCount}
     */
    public void setPartCount(Integer partCount) { this.partCount = partCount; }
    /**
     * 读取{@link #status}。
     *
     * @return 当前值，含义与约束见{@link #status}
     */
    public FileUploadStatus getStatus() { return status; }
    /**
     * 更新{@link #status}。
     *
     * @param status 新值，含义与约束见{@link #status}
     */
    public void setStatus(FileUploadStatus status) { this.status = status; }
    /**
     * 读取{@link #expiresAt}。
     *
     * @return 当前值，含义与约束见{@link #expiresAt}
     */
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    /**
     * 更新{@link #expiresAt}。
     *
     * @param expiresAt 新值，含义与约束见{@link #expiresAt}
     */
    public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }
    /**
     * 读取{@link #lastActiveAt}。
     *
     * @return 当前值，含义与约束见{@link #lastActiveAt}
     */
    public OffsetDateTime getLastActiveAt() { return lastActiveAt; }
    /**
     * 更新{@link #lastActiveAt}。
     *
     * @param lastActiveAt 新值，含义与约束见{@link #lastActiveAt}
     */
    public void setLastActiveAt(OffsetDateTime lastActiveAt) { this.lastActiveAt = lastActiveAt; }
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
     * 读取{@link #completedAt}。
     *
     * @return 当前值，含义与约束见{@link #completedAt}
     */
    public OffsetDateTime getCompletedAt() { return completedAt; }
    /**
     * 更新{@link #completedAt}。
     *
     * @param completedAt 新值，含义与约束见{@link #completedAt}
     */
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }
    /**
     * 读取{@link #completionOwner}。
     *
     * @return 当前值，含义与约束见{@link #completionOwner}
     */
    public String getCompletionOwner() { return completionOwner; }
    /**
     * 更新{@link #completionOwner}。
     *
     * @param completionOwner 新值，含义与约束见{@link #completionOwner}
     */
    public void setCompletionOwner(String completionOwner) { this.completionOwner = completionOwner; }
    /**
     * 读取{@link #completionLeaseExpiresAt}。
     *
     * @return 当前值，含义与约束见{@link #completionLeaseExpiresAt}
     */
    public OffsetDateTime getCompletionLeaseExpiresAt() { return completionLeaseExpiresAt; }
    /**
     * 更新{@link #completionLeaseExpiresAt}。
     *
     * @param completionLeaseExpiresAt 新值，含义与约束见{@link #completionLeaseExpiresAt}
     */
    public void setCompletionLeaseExpiresAt(OffsetDateTime completionLeaseExpiresAt) { this.completionLeaseExpiresAt = completionLeaseExpiresAt; }
}
