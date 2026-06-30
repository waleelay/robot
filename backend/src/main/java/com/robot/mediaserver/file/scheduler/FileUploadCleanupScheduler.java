package com.robot.mediaserver.file.scheduler;

import com.robot.mediaserver.file.model.FileUploadStatus;
import com.robot.mediaserver.file.repository.MediaFileUploadRepository;
import com.robot.mediaserver.file.service.FileService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FileUploadCleanupScheduler {

    private final MediaFileUploadRepository repository;
    private final FileService service;

    public FileUploadCleanupScheduler(MediaFileUploadRepository repository, FileService service) {
        this.repository = repository;
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${media.file.cleanup-delay-ms:3600000}")
    public void cleanup() {
        repository.findTop20ByStatusAndExpiresAtBeforeOrderByExpiresAtAsc(
                        FileUploadStatus.ACTIVE,
                        OffsetDateTime.now(ZoneOffset.UTC))
                .forEach(service::expireUpload);
    }
}
