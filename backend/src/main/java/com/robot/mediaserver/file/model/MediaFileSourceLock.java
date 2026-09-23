package com.robot.mediaserver.file.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** 同一机器人来源文件首次创建时使用的数据库互斥行。 */
@Entity
@Table(name = "media_file_source_lock")
public class MediaFileSourceLock {

    @Id
    @Column(length = 64)
    private String lockId;

    @Column(nullable = false, length = 64)
    private String robotId;

    @Column(nullable = false, length = 256)
    private String sourceFileId;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    public String getLockId() { return lockId; }
    public void setLockId(String lockId) { this.lockId = lockId; }
    public String getRobotId() { return robotId; }
    public void setRobotId(String robotId) { this.robotId = robotId; }
    public String getSourceFileId() { return sourceFileId; }
    public void setSourceFileId(String sourceFileId) { this.sourceFileId = sourceFileId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
