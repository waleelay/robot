package com.robot.mediaserver.file.repository;

import com.robot.mediaserver.file.model.MediaFileSourceLock;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 保存并锁定机器人来源文件互斥行，串行化同一来源文件的首次创建。 */
public interface MediaFileSourceLockRepository extends JpaRepository<MediaFileSourceLock, String> {

    /**
     * 幂等创建来源文件锁记录；唯一键存在时不覆盖已有锁信息。
     *
     * @param lockId 互斥锁记录的唯一标识
     * @param robotId 机器人 ID
     * @param sourceFileId 源文件 ID
     * @param createdAt 创建时间
     * @return 实际插入的行数
     */
    @Modifying
    @Query(value = "insert ignore into media_file_source_lock "
            + "(lock_id, robot_id, source_file_id, created_at) values (:lockId, :robotId, :sourceFileId, :createdAt)",
            nativeQuery = true)
    int insertIgnore(
            @Param("lockId") String lockId,
            @Param("robotId") String robotId,
            @Param("sourceFileId") String sourceFileId,
            @Param("createdAt") OffsetDateTime createdAt);

    /**
     * 在当前事务中锁定来源文件互斥行，同源创建流程必须持锁执行。
     *
     * @param lockId 互斥锁记录的唯一标识
     * @return 已加行锁的来源记录；不存在时为空
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from MediaFileSourceLock l where l.lockId = :lockId")
    Optional<MediaFileSourceLock> findByIdForUpdate(@Param("lockId") String lockId);
}
