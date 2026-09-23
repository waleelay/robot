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
 * 使用短数据库事务协调 multipart 合并，避免 Compose 大文件期间长期占用数据库连接和行锁。
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
    private final int leaseSeconds;

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

    public FileStatusResponse complete(String robotIdHeader, String uploadId) {
        String robotId = requiredRobotId(robotIdHeader);
        String owner = UUID.randomUUID().toString().replace("-", "");
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

    private void finish(CompletionContext context) {
        MediaFileUpload upload = uploadRepository.findByIdForUpdate(context.uploadId())
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "UPLOAD_NOT_FOUND", "未找到上传任务"));
        if (upload.getStatus() == FileUploadStatus.COMPLETED) {
            return;
        }
        if (upload.getStatus() != FileUploadStatus.COMPLETING) {
            throw error(HttpStatus.CONFLICT, "UPLOAD_NOT_ACTIVE", "上传会话完成状态发生变化");
        }
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
            log.error("释放 multipart 完成状态失败: uploadId={}", context.uploadId(), releaseError);
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
                log.warn("multipart 合并续租失败，执行权可能已转移: uploadId={}", context.uploadId());
            }
        } catch (RuntimeException exception) {
            log.warn("multipart 合并续租异常: uploadId={}", context.uploadId(), exception);
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
