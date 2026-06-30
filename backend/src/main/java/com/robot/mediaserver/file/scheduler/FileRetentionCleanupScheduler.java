package com.robot.mediaserver.file.scheduler;

import com.robot.mediaserver.config.MediaProperties;
import com.robot.mediaserver.file.model.FileStatus;
import com.robot.mediaserver.file.repository.MediaFileRepository;
import com.robot.mediaserver.file.service.FileService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FileRetentionCleanupScheduler {

    private final MediaProperties properties;
    private final MediaFileRepository repository;
    private final FileService service;

    public FileRetentionCleanupScheduler(MediaProperties properties, MediaFileRepository repository, FileService service) {
        this.properties = properties;
        this.repository = repository;
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${media.file.retention-cleanup-delay-ms:3600000}")
    public void cleanup() {
        OffsetDateTime threshold = OffsetDateTime.now(ZoneOffset.UTC).minusDays(properties.getFile().getRetentionDays());
        repository.findTop10ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(FileStatus.READY, threshold)
                .forEach(service::deleteFileAssets);
    }
}
