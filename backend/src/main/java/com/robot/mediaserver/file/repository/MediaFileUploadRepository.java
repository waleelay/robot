package com.robot.mediaserver.file.repository;

import com.robot.mediaserver.file.model.FileUploadStatus;
import com.robot.mediaserver.file.model.MediaFileUpload;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MediaFileUploadRepository extends JpaRepository<MediaFileUpload, String> {

    Optional<MediaFileUpload> findFirstByFileIdAndStatusOrderByCreatedAtDesc(String fileId, FileUploadStatus status);

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
