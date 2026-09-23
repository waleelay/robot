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

public interface MediaFileUploadRepository extends JpaRepository<MediaFileUpload, String> {

    Optional<MediaFileUpload> findFirstByFileIdAndStatusOrderByCreatedAtDesc(String fileId, FileUploadStatus status);

    Optional<MediaFileUpload> findByStorageUploadId(String storageUploadId);

    List<MediaFileUpload> findByFileIdInOrderByCreatedAtDesc(Collection<String> fileIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from MediaFileUpload u where u.uploadId = :uploadId")
    Optional<MediaFileUpload> findByIdForUpdate(@Param("uploadId") String uploadId);

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

    List<MediaFileUpload> findTop100ByStatusAndExpiresAtBeforeOrderByExpiresAtAsc(
            FileUploadStatus status,
            OffsetDateTime expiresAt);

    long countByStatusAndExpiresAtAfter(FileUploadStatus status, OffsetDateTime expiresAt);

    @Query("select count(u) from MediaFileUpload u, MediaFile f "
            + "where u.fileId = f.fileId and u.status = :status and u.expiresAt > :expiresAt "
            + "and f.robotId = :robotId")
    long countActiveByRobotId(
            @Param("robotId") String robotId,
            @Param("status") FileUploadStatus status,
            @Param("expiresAt") OffsetDateTime expiresAt);
}
