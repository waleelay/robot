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

/** 逐项清理过期上传会话及超时手动录像，单项失败保留上下文并继续后续清理。 */
@Component
public class FileUploadCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(FileUploadCleanupScheduler.class);

    private final MediaFileUploadRepository repository;
    private final FileService service;

    /**
     * 初始化 FileUploadCleanupScheduler，保存所需依赖及初始运行状态。
     * @param repository 分片上传会话仓储
     * @param service 管理文件元数据、上传会话、所有权与播放授权，协调存储及视频处理状态。
     */
    public FileUploadCleanupScheduler(MediaFileUploadRepository repository, FileService service) {
        this.repository = repository;
        this.service = service;
    }

    /**
     * 清理过期分片会话，终止对象存储上传并同步文件状态。
     */
    @Scheduled(fixedDelayString = "${media.file.cleanup-delay-ms:60000}")
    public void cleanup() {
        repository.findTop100ByStatusAndExpiresAtBeforeOrderByExpiresAtAsc(
                        FileUploadStatus.ACTIVE,
                        OffsetDateTime.now(ZoneOffset.UTC))
                .forEach(upload -> {
                    try {
                        service.expireUpload(upload);
                    } catch (RuntimeException ex) {
                        log.warn("清理过期上传会话失败: 上传标识={}, 文件标识={}",
                                upload.getUploadId(), upload.getFileId(), ex);
                    }
                });
        service.expiredLiveRecordingIds().forEach(fileId -> {
            try {
                service.expireLiveRecording(fileId);
            } catch (RuntimeException ex) {
                log.warn("停止超时手动录像失败: 文件标识={}", fileId, ex);
            }
        });
    }
}
