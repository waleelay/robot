package com.robot.mediaserver.file.service;

import com.robot.mediaserver.config.MediaProperties;
import com.robot.mediaserver.file.api.FileApiException;
import com.robot.mediaserver.file.dto.FileStatusResponse;
import com.robot.mediaserver.file.model.FileUploadStatus;
import com.robot.mediaserver.file.model.MediaFile;
import com.robot.mediaserver.file.model.MediaFileUpload;
import com.robot.mediaserver.file.repository.MediaFileRepository;
import com.robot.mediaserver.file.repository.MediaFileUploadRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 使用短数据库事务协调分片合并，避免 拼接大文件期间长期占用数据库连接和行锁。
 */
@Service
public class FileMultipartCompletionService {

    private static final Logger log = LoggerFactory.getLogger(FileMultipartCompletionService.class);

    private final MediaFileRepository fileRepository;
    private final MediaFileUploadRepository uploadRepository;
    private final FileObjectStorageService storage;
    private final FileService fileService;
    private final TransactionTemplate transactionTemplate;
    private final TaskScheduler leaseScheduler;
    /**
     * 分片合并处理权的有效秒数，最小为 30 秒；合并期间由调度器续租。
     */
    private final int leaseSeconds;

    /**
     * 初始化 FileMultipartCompletionService，保存所需依赖及初始运行状态。
     *
     * @param fileRepository 查询文件主记录，支持租户过滤、来源复用和保留期清理。
     * @param uploadRepository 访问上传会话及配额计数，以悲观锁和带所有者条件的更新维护合并租约。
     * @param storage 封装 MinIO 对象和分片读写、签名地址及桶初始化；调用方负责传入流的生命周期。
     * @param fileService 管理文件元数据、上传会话、所有权与播放授权，协调存储及视频处理状态。
     * @param transactionManager 数据库事务管理器
     * @param leaseScheduler 后台任务调度器
     * @param properties 服务配置
     */
    public FileMultipartCompletionService(
            MediaFileRepository fileRepository,
            MediaFileUploadRepository uploadRepository,
            FileObjectStorageService storage,
            FileService fileService,
            PlatformTransactionManager transactionManager,
            @Qualifier("fileCompletionLeaseScheduler") TaskScheduler leaseScheduler,
            MediaProperties properties) {
        this.fileRepository = fileRepository;
        this.uploadRepository = uploadRepository;
        this.storage = storage;
        this.fileService = fileService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.leaseScheduler = leaseScheduler;
        this.leaseSeconds = Math.max(30, properties.getFile().getCompletionLeaseSeconds());
    }

    /**
     * 以有期限的处理权合并分片；重复完成返回现有结果，其他持有者处理中则报告可重试冲突。
     *
     * @param robotIdHeader 可信机器人标识请求头
     * @param uploadId 平台上传会话 ID
     * @return 合并后的上传响应，视频后处理可能仍未完成
     */
    public FileStatusResponse complete(String robotIdHeader, String uploadId) {
        String robotId = requiredRobotId(robotIdHeader);
        String owner = UUID.randomUUID().toString().replace("-", "");
        // 行锁只用于领取处理权；大文件 Compose 必须在事务外执行，避免长期占用连接和锁。
        CompletionContext context = transactionTemplate.execute(status -> claim(robotId, uploadId, owner));
        if (context == null) {
            throw error(HttpStatus.CONFLICT, "UPLOAD_COMPLETE_FAILED", "无法获取上传完成上下文");
        }
        if (context.completed()) {
            return fileService.fileStatus(robotId, context.fileId());
        }
        if (context.inProgress()) {
            throw new FileApiException(
                    HttpStatus.CONFLICT,
                    "UPLOAD_COMPLETION_IN_PROGRESS",
                    "上传文件正在合并，请稍后重试",
                    true,
                    Map.of("retryAfterSeconds", Math.max(1, leaseSeconds / 3)));
        }

        ScheduledFuture<?> heartbeat = null;
        try {
            heartbeat = leaseScheduler.scheduleAtFixedRate(
                    () -> renewLease(context),
                    Duration.ofSeconds(Math.max(10, leaseSeconds / 3)));
            // 上次 Compose 成功但元数据提交失败时复用源对象，随后仍校验大小并核对租约持有者。
            Long existingSize = storage.statSizeIfExists(context.objectKey());
            if (existingSize == null) {
                List<FileObjectStorageService.StoredPart> parts =
                        storage.listParts(context.objectKey(), context.storageUploadId());
                validateUploadedParts(context, parts);
                storage.completeMultipart(context.objectKey(), context.storageUploadId(), parts);
                existingSize = storage.statSize(context.objectKey());
            }
            if (existingSize != context.fileSize()) {
                throw error(HttpStatus.CONFLICT, "UPLOAD_SIZE_MISMATCH", "合成后的对象大小与登记文件不一致");
            }
            transactionTemplate.executeWithoutResult(status -> finish(context));
            return fileService.fileStatus(robotId, context.fileId());
        } catch (RuntimeException exception) {
            releaseForRetry(context);
            throw exception;
        } finally {
            if (heartbeat != null) {
                heartbeat.cancel(false);
            }
        }
    }

    /**
     * 在上传行锁内校验机器人归属并领取合并租约；已完成或其他未过期持有者的任务不重复领取。
     */
    private CompletionContext claim(String robotId, String uploadId, String owner) {
        MediaFileUpload upload = uploadRepository.findByIdForUpdate(uploadId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "UPLOAD_NOT_FOUND", "未找到上传任务"));
        MediaFile file = fileRepository.findById(upload.getFileId())
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "FILE_NOT_FOUND", "未找到文件"));
        if (!Objects.equals(file.getRobotId(), robotId)) {
            throw error(HttpStatus.NOT_FOUND, "UPLOAD_NOT_FOUND", "未找到上传任务");
        }
        if (upload.getStatus() == FileUploadStatus.COMPLETED) {
            return context(file, upload, owner, true, false);
        }
        if (upload.getStatus() != FileUploadStatus.ACTIVE
                && upload.getStatus() != FileUploadStatus.COMPLETING) {
            throw error(HttpStatus.CONFLICT, "UPLOAD_NOT_ACTIVE", "上传会话已不再有效");
        }
        if (upload.getStatus() == FileUploadStatus.COMPLETING
                && upload.getCompletionLeaseExpiresAt() != null
                && upload.getCompletionLeaseExpiresAt().isAfter(now())) {
            return context(file, upload, owner, false, true);
        }
        upload.setStatus(FileUploadStatus.COMPLETING);
        upload.setCompletionOwner(owner);
        upload.setCompletionLeaseExpiresAt(now().plusSeconds(leaseSeconds));
        upload.setLastActiveAt(now());
        uploadRepository.save(upload);
        return context(file, upload, owner, false, false);
    }

    /**
     * 在短事务内再次核验租约持有者后提交完成状态，防止旧工作者覆盖接管者。
     */
    private void finish(CompletionContext context) {
        MediaFileUpload upload = uploadRepository.findByIdForUpdate(context.uploadId())
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "UPLOAD_NOT_FOUND", "未找到上传任务"));
        if (upload.getStatus() == FileUploadStatus.COMPLETED) {
            return;
        }
        if (upload.getStatus() != FileUploadStatus.COMPLETING) {
            throw error(HttpStatus.CONFLICT, "UPLOAD_NOT_ACTIVE", "上传会话完成状态发生变化");
        }
        // 旧工作者即使完成存储操作，也不能覆盖已接管任务的新持有者。
        if (!Objects.equals(upload.getCompletionOwner(), context.owner())) {
            throw error(HttpStatus.CONFLICT, "UPLOAD_COMPLETION_LEASE_LOST", "上传合并执行权已转移");
        }
        upload.setStatus(FileUploadStatus.COMPLETED);
        upload.setCompletedAt(now());
        upload.setLastActiveAt(now());
        upload.setCompletionOwner(null);
        upload.setCompletionLeaseExpiresAt(null);
        uploadRepository.save(upload);
        fileService.markMultipartUploaded(context.fileId());
    }

    /**
     * 失败后只释放仍属于本次持有者的合并租约，恢复 ACTIVE 以允许重试。
     */
    private void releaseForRetry(CompletionContext context) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                uploadRepository.findByIdForUpdate(context.uploadId()).ifPresent(upload -> {
                    if (upload.getStatus() == FileUploadStatus.COMPLETING
                            && Objects.equals(upload.getCompletionOwner(), context.owner())) {
                        upload.setStatus(FileUploadStatus.ACTIVE);
                        upload.setLastActiveAt(now());
                        upload.setCompletionOwner(null);
                        upload.setCompletionLeaseExpiresAt(null);
                        uploadRepository.save(upload);
                    }
                });
            });
        } catch (RuntimeException releaseError) {
            log.error("释放分片上传完成状态失败，上传标识={}", context.uploadId(), releaseError);
        }
    }

    private void renewLease(CompletionContext context) {
        try {
            OffsetDateTime timestamp = now();
            int updated = uploadRepository.renewCompletionLease(
                    context.uploadId(),
                    context.owner(),
                    FileUploadStatus.COMPLETING,
                    timestamp,
                    timestamp.plusSeconds(leaseSeconds));
            if (updated == 0) {
                log.warn("分片合并续租失败，执行权可能已转移，上传标识={}", context.uploadId());
            }
        } catch (RuntimeException exception) {
            log.warn("分片合并续租异常，上传标识={}", context.uploadId(), exception);
        }
    }

    private void validateUploadedParts(
            CompletionContext context,
            List<FileObjectStorageService.StoredPart> parts) {
        if (parts.size() != context.partCount()) {
            throw error(HttpStatus.CONFLICT, "UPLOAD_INCOMPLETE", "仍有分片尚未上传");
        }
        long total = 0;
        for (int index = 0; index < parts.size(); index++) {
            FileObjectStorageService.StoredPart part = parts.get(index);
            if (part.partNumber() != index + 1) {
                throw error(HttpStatus.CONFLICT, "UPLOAD_INCOMPLETE", "已上传分片不连续");
            }
            total += part.size();
        }
        if (total != context.fileSize()) {
            throw error(HttpStatus.CONFLICT, "UPLOAD_SIZE_MISMATCH", "已上传分片大小与登记文件不一致");
        }
    }

    private CompletionContext context(
            MediaFile file,
            MediaFileUpload upload,
            String owner,
            boolean completed,
            boolean inProgress) {
        return new CompletionContext(
                file.getFileId(),
                upload.getUploadId(),
                file.getObjectKey(),
                upload.getStorageUploadId(),
                file.getFileSize(),
                upload.getPartCount(),
                owner,
                completed,
                inProgress);
    }

    private String requiredRobotId(String robotId) {
        if (robotId == null || robotId.isBlank()) {
            throw error(HttpStatus.UNAUTHORIZED, "ROBOT_ID_REQUIRED", "机器人上传请求缺少 X-Robot-Id");
        }
        return robotId.trim();
    }

    private FileApiException error(HttpStatus status, String code, String message) {
        return new FileApiException(status, code, message, false, Map.of());
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    /**
     * 在数据库短事务中取得的合并上下文，供事务外存储合并及后续租约校验使用。
     * @param fileId 文件 ID
     * @param uploadId 平台上传会话 ID
     * @param objectKey 对象在存储桶内的键，不包含访问凭据
     * @param storageUploadId 平台生成的暂存分片会话标识，用于组织分片对象键
     * @param fileSize 文件字节数
     * @param partCount 上传会话登记的分片总数
     * @param owner 当前资源或处理租约的持有者
     * @param completed 当前上传是否已完成合并
     * @param inProgress 是否已有其他持有者正在合并分片
     */
    private record CompletionContext(
            String fileId,
            String uploadId,
            String objectKey,
            String storageUploadId,
            long fileSize,
            int partCount,
            String owner,
            boolean completed,
            boolean inProgress) {
    }
}
