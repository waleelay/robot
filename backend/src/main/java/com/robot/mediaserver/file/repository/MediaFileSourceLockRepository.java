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

public interface MediaFileSourceLockRepository extends JpaRepository<MediaFileSourceLock, String> {

    @Modifying
    @Query(value = "insert ignore into media_file_source_lock "
            + "(lock_id, robot_id, source_file_id, created_at) values (:lockId, :robotId, :sourceFileId, :createdAt)",
            nativeQuery = true)
    int insertIgnore(
            @Param("lockId") String lockId,
            @Param("robotId") String robotId,
            @Param("sourceFileId") String sourceFileId,
            @Param("createdAt") OffsetDateTime createdAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from MediaFileSourceLock l where l.lockId = :lockId")
    Optional<MediaFileSourceLock> findByIdForUpdate(@Param("lockId") String lockId);
}
