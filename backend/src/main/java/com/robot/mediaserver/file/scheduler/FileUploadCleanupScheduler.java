package com.robot.mediaserver.file.scheduler;

import com.robot.mediaserver.file.model.FileUploadStatus;
import com.robot.mediaserver.file.repository.MediaFileUploadRepository;
import com.robot.mediaserver.file.service.FileService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FileUploadCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(FileUploadCleanupScheduler.class);

    private final MediaFileUploadRepository repository;
    private final FileService service;

    public FileUploadCleanupScheduler(MediaFileUploadRepository repository, FileService service) {
        this.repository = repository;
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${media.file.cleanup-delay-ms:60000}")
    public void cleanup() {
        repository.findTop100ByStatusAndExpiresAtBeforeOrderByExpiresAtAsc(
                        FileUploadStatus.ACTIVE,
                        OffsetDateTime.now(ZoneOffset.UTC))
                .forEach(upload -> {
                    try {
                        service.expireUpload(upload);
                    } catch (RuntimeException ex) {
                        log.warn("清理过期上传会话失败: uploadId={}, fileId={}",
                                upload.getUploadId(), upload.getFileId(), ex);
                    }
                });
    }
}
