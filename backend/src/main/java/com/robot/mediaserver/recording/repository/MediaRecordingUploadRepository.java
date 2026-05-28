package com.robot.mediaserver.recording.repository;

import com.robot.mediaserver.recording.model.MediaRecordingUpload;
import com.robot.mediaserver.recording.model.UploadStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MediaRecordingUploadRepository extends JpaRepository<MediaRecordingUpload, String> {

    Optional<MediaRecordingUpload> findFirstByRecordingIdAndStatusOrderByCreatedAtDesc(
            String recordingId,
            UploadStatus status);

    long countByStatus(UploadStatus status);

    @Query("select count(u) from MediaRecordingUpload u, MediaRecording r "
            + "where u.recordingId = r.recordingId and u.status = :status and r.robotId = :robotId")
    long countActiveByRobotId(@Param("robotId") String robotId, @Param("status") UploadStatus status);

    List<MediaRecordingUpload> findTop20ByStatusAndExpiresAtBeforeOrderByExpiresAtAsc(
            UploadStatus status,
            OffsetDateTime expiresAt);
}
