package com.robot.mediaserver.recording.scheduler;

import com.robot.mediaserver.config.MediaProperties;
import com.robot.mediaserver.recording.model.RecordingStatus;
import com.robot.mediaserver.recording.repository.MediaRecordingRepository;
import com.robot.mediaserver.recording.service.RecordingService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RecordingRetentionCleanupScheduler {

    private final MediaProperties properties;
    private final MediaRecordingRepository repository;
    private final RecordingService service;

    public RecordingRetentionCleanupScheduler(
            MediaProperties properties,
            MediaRecordingRepository repository,
            RecordingService service) {
        this.properties = properties;
        this.repository = repository;
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${media.recording.retention-cleanup-delay-ms:3600000}")
    public void cleanup() {
        OffsetDateTime threshold = OffsetDateTime.now(ZoneOffset.UTC)
                .minusDays(properties.getRecording().getRetentionDays());
        repository.findTop10ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(RecordingStatus.READY, threshold)
                .forEach(service::deleteRecordingAssets);
        repository.findTop10ByStatusOrderByUpdatedAtAsc(RecordingStatus.DELETING)
                .forEach(service::deleteRecordingAssets);
    }
}
