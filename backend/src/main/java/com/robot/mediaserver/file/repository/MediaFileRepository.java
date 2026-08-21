package com.robot.mediaserver.file.repository;

import com.robot.media.common.file.FileStatus;
import com.robot.media.common.file.FileType;
import com.robot.mediaserver.file.model.MediaFile;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MediaFileRepository extends JpaRepository<MediaFile, String>, JpaSpecificationExecutor<MediaFile> {

    Optional<MediaFile> findByRobotIdAndSourceFileId(String robotId, String sourceFileId);

    List<MediaFile> findTop10ByFileTypeAndStatusOrderByUpdatedAtAsc(FileType fileType, FileStatus status);

    Optional<MediaFile> findFirstByFileTypeAndStatusAndSourceFileIdStartingWithOrderByUpdatedAtAsc(
            FileType fileType,
            FileStatus status,
            String sourceFileIdPrefix);

    List<MediaFile> findTop100ByFileTypeAndStatusAndSourceFileIdStartingWithAndCreatedAtBeforeOrderByCreatedAtAsc(
            FileType fileType,
            FileStatus status,
            String sourceFileIdPrefix,
            OffsetDateTime createdAt);

    List<MediaFile> findTop10ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(FileStatus status, OffsetDateTime updatedAt);

    List<MediaFile> findTop10ByStatusOrderByUpdatedAtAsc(FileStatus status);
}
