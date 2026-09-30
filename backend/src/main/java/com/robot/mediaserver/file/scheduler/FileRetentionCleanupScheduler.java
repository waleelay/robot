package com.robot.mediaserver.file.scheduler;

import com.robot.mediaserver.config.MediaProperties;
import com.robot.media.common.file.FileStatus;
import com.robot.mediaserver.file.repository.MediaFileRepository;
import com.robot.mediaserver.file.service.FileService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 清理超过保留期限的 READY 文件；实际对象删除与记录状态由 FileService 收口。 */
@Component
public class FileRetentionCleanupScheduler {

    private final MediaProperties properties;
    private final MediaFileRepository repository;
    private final FileService service;

    /**
     * 初始化 FileRetentionCleanupScheduler，保存所需依赖及初始运行状态。
     * @param properties 服务配置
     * @param repository 文件主记录仓储
     * @param service 管理文件元数据、上传会话、所有权与播放授权，协调存储及视频处理状态。
     */
    public FileRetentionCleanupScheduler(MediaProperties properties, MediaFileRepository repository, FileService service) {
        this.properties = properties;
        this.repository = repository;
        this.service = service;
    }

    /**
     * 分批清理超过保留期的文件及其存储资产，按配置限制每轮处理范围。
     */
    @Scheduled(fixedDelayString = "${media.file.retention-cleanup-delay-ms:3600000}")
    public void cleanup() {
        OffsetDateTime threshold = OffsetDateTime.now(ZoneOffset.UTC).minusDays(properties.getFile().getRetentionDays());
        repository.findTop10ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(FileStatus.READY, threshold)
                .forEach(service::deleteFileAssets);
    }
}
