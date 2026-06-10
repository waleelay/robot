package com.robot.mediaserver.recording.repository;

import com.robot.mediaserver.recording.model.MediaRecording;
import com.robot.mediaserver.recording.model.RecordingStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MediaRecordingRepository extends JpaRepository<MediaRecording, String>, JpaSpecificationExecutor<MediaRecording> {

    Optional<MediaRecording> findByRobotIdAndSourceFileId(String robotId, String sourceFileId);

    Optional<MediaRecording> findFirstBySessionIdAndSourceTypeAndStatusOrderByCreatedAtDesc(
            String sessionId,
            String sourceType,
            RecordingStatus status);

    List<MediaRecording> findTop20ByOrgIdAndStatusOrderByRecordedStartedAtDesc(
            String orgId,
            RecordingStatus status);

    List<MediaRecording> findTop20ByOrgIdAndRobotIdAndStatusOrderByRecordedStartedAtDesc(
            String orgId,
            String robotId,
            RecordingStatus status);

    List<MediaRecording> findTop20ByOrgIdAndRobotIdAndDeviceIdAndStatusOrderByRecordedStartedAtDesc(
            String orgId,
            String robotId,
            String deviceId,
            RecordingStatus status);

    List<MediaRecording> findTop10ByStatusAndProcessingLeaseUntilBeforeOrderByUpdatedAtAsc(
            RecordingStatus status,
            OffsetDateTime leaseExpiredBefore);

    List<MediaRecording> findTop10ByStatusAndProcessingLeaseUntilIsNullOrderByUpdatedAtAsc(
            RecordingStatus status);

    List<MediaRecording> findTop10ByStatusOrderByUpdatedAtAsc(RecordingStatus status);

    List<MediaRecording> findTop10ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
            RecordingStatus status,
            OffsetDateTime updatedBefore);
}
