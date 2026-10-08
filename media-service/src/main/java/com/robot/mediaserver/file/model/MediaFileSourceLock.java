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

    /**
     * 互斥锁记录的唯一标识。
     */
    @Id
    @Column(length = 64)
    private String lockId;

    /**
     * 机器人 ID。
     */
    @Column(nullable = false, length = 64)
    private String robotId;

    /**
     * 源文件 ID。
     */
    @Column(nullable = false, length = 256)
    private String sourceFileId;

    /**
     * 创建时间。
     */
    @Column(nullable = false)
    private OffsetDateTime createdAt;

    /**
     * 读取{@link #lockId}。
     *
     * @return 当前值，含义与约束见{@link #lockId}
     */
    public String getLockId() { return lockId; }
    /**
     * 更新{@link #lockId}。
     *
     * @param lockId 新值，含义与约束见{@link #lockId}
     */
    public void setLockId(String lockId) { this.lockId = lockId; }
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
}
