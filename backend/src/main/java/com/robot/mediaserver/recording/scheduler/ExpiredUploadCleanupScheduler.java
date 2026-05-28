package com.robot.mediaserver.recording.scheduler;

import com.robot.mediaserver.recording.model.UploadStatus;
import com.robot.mediaserver.recording.repository.MediaRecordingUploadRepository;
import com.robot.mediaserver.recording.service.RecordingService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ExpiredUploadCleanupScheduler {

    private final MediaRecordingUploadRepository repository;
    private final RecordingService service;

    public ExpiredUploadCleanupScheduler(MediaRecordingUploadRepository repository, RecordingService service) {
        this.repository = repository;
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${media.recording.cleanup-delay-ms:3600000}")
    public void cleanup() {
        repository.findTop20ByStatusAndExpiresAtBeforeOrderByExpiresAtAsc(
                        UploadStatus.ACTIVE,
                        OffsetDateTime.now(ZoneOffset.UTC))
                .forEach(service::expireUpload);
    }
}
