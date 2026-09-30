package com.robot.mediaserver.file.repository;

import com.robot.mediaserver.file.model.FileUploadStatus;
import com.robot.mediaserver.file.model.MediaFileUpload;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Collection;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** 访问上传会话及配额计数，以悲观锁和带所有者条件的更新维护合并租约。 */
public interface MediaFileUploadRepository extends JpaRepository<MediaFileUpload, String> {

    /**
     * 查找文件在指定状态下最新的上传会话。
     *
     * @param fileId 文件 ID
     * @param status 当前业务状态，取值遵循所属模型的状态协议
     * @return 最新会话；不存在时为空
     */
    Optional<MediaFileUpload> findFirstByFileIdAndStatusOrderByCreatedAtDesc(String fileId, FileUploadStatus status);

    /**
     * 按平台暂存分片会话标识查找上传记录。
     *
     * @param storageUploadId 平台生成的暂存分片会话标识，用于组织分片对象键
     * @return 匹配的上传会话；不存在时为空
     */
    Optional<MediaFileUpload> findByStorageUploadId(String storageUploadId);

    /**
     * 批量查询文件的上传会话，按新建时间倒序返回。
     * @param fileIds 待查询的通用文件 ID 集合
     * @return 匹配上传会话列表
     */
    List<MediaFileUpload> findByFileIdInOrderByCreatedAtDesc(Collection<String> fileIds);

    /**
     * 在当前事务中锁定上传会话，串行化合并权竞争与终态更新。
     *
     * @param uploadId 平台上传会话 ID
     * @return 已加锁会话；不存在时为空
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from MediaFileUpload u where u.uploadId = :uploadId")
    Optional<MediaFileUpload> findByIdForUpdate(@Param("uploadId") String uploadId);

    /**
     * 仅为仍由指定持有者处理的合并任务续租，避免过期工作者覆盖新持有者。
     *
     * @param uploadId 平台上传会话 ID
     * @param owner 当前资源或处理租约的持有者
     * @param status 当前业务状态，取值遵循所属模型的状态协议
     * @param now 本次处理使用的统一服务端时间
     * @param leaseExpiresAt 本次续租后的有效截止时间
     * @return 成功续租的行数
     */
    @Modifying
    @Transactional
    @Query("update MediaFileUpload u set u.lastActiveAt = :now, u.completionLeaseExpiresAt = :leaseExpiresAt "
            + "where u.uploadId = :uploadId and u.status = :status and u.completionOwner = :owner")
    int renewCompletionLease(
            @Param("uploadId") String uploadId,
            @Param("owner") String owner,
            @Param("status") FileUploadStatus status,
            @Param("now") OffsetDateTime now,
            @Param("leaseExpiresAt") OffsetDateTime leaseExpiresAt);

    /**
     * 选择最多一百个已过期的指定状态上传会话。
     *
     * @param status 当前业务状态，取值遵循所属模型的状态协议
     * @param expiresAt 有效期截止时间
     * @return 按到期时间升序排列的会话
     */
    List<MediaFileUpload> findTop100ByStatusAndExpiresAtBeforeOrderByExpiresAtAsc(
            FileUploadStatus status,
            OffsetDateTime expiresAt);

    /**
     * 统计指定状态且尚未过期的上传会话。
     *
     * @param status 当前业务状态，取值遵循所属模型的状态协议
     * @param expiresAt 有效期截止时间
     * @return 当前有效会话数
     */
    long countByStatusAndExpiresAtAfter(FileUploadStatus status, OffsetDateTime expiresAt);

    /**
     * 统计指定机器人的有效上传会话，供配额检查。
     *
     * @param robotId 机器人 ID
     * @param status 当前业务状态，取值遵循所属模型的状态协议
     * @param expiresAt 有效期截止时间
     * @return 该机器人的活动上传数量
     */
    @Query("select count(u) from MediaFileUpload u, MediaFile f "
            + "where u.fileId = f.fileId and u.status = :status and u.expiresAt > :expiresAt "
            + "and f.robotId = :robotId")
    long countActiveByRobotId(
            @Param("robotId") String robotId,
            @Param("status") FileUploadStatus status,
            @Param("expiresAt") OffsetDateTime expiresAt);
}
