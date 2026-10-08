package com.robot.mediaserver.file.progress;

import com.fasterxml.jackson.databind.JsonNode;
import com.robot.media.common.file.FileStatus;
import com.robot.mediaserver.config.MediaProperties;
import com.robot.mediaserver.file.api.FileApiException;
import com.robot.mediaserver.file.model.FileUploadStatus;
import com.robot.mediaserver.file.model.MediaFile;
import com.robot.mediaserver.file.model.MediaFileUpload;
import com.robot.mediaserver.file.repository.MediaFileRepository;
import com.robot.mediaserver.file.repository.MediaFileUploadRepository;
import com.robot.mediaserver.file.service.FileObjectStorageService;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** 合并 MinIO 分片通知与 Redis 进度快照，缓存缺失时通过有界回源重建状态。 */
@Service
public class FileUploadProgressService {

    private static final Logger log = LoggerFactory.getLogger(FileUploadProgressService.class);
    private static final Pattern PART_KEY = Pattern.compile("^.+\\.upload-parts/([^/]+)/part-(\\d+)$");
    private static final String SUMMARY_PREFIX = "media:file-upload:";
    private static final String STORAGE_PREFIX = "media:file-upload:storage:";
    /**
     * 数据库回退进度使用的版本值，不代表 Redis 分片事件版本连续性。
     */
    private static final long TERMINAL_VERSION = 0L;

    // 此处只需要脚本副作用，不消费 Lua 返回的列表；使用通配符保留运行时 List.class 而不泄漏原始类型。
    /**
     * 原子更新分片摘要；同分片同大小和 ETag 的重复事件只续期，不增加计数和版本。
     */
    private static final DefaultRedisScript<?> RECORD_PART_SCRIPT = new DefaultRedisScript<>("""
            local old = redis.call('HGET', KEYS[2], ARGV[1])
            local value = ARGV[2] .. ':' .. ARGV[3]
            redis.call('HSETNX', KEYS[1], 'fileId', ARGV[6])
            redis.call('HSETNX', KEYS[1], 'uploadId', ARGV[7])
            redis.call('HSETNX', KEYS[1], 'fileName', ARGV[8])
            redis.call('HSETNX', KEYS[1], 'fileType', ARGV[9])
            redis.call('HSETNX', KEYS[1], 'totalBytes', ARGV[10])
            redis.call('HSETNX', KEYS[1], 'partCount', ARGV[11])
            redis.call('HSETNX', KEYS[1], 'uploadedBytes', '0')
            redis.call('HSETNX', KEYS[1], 'uploadedPartCount', '0')
            redis.call('HSETNX', KEYS[1], 'version', '0')
            if old == value then
                redis.call('EXPIRE', KEYS[1], ARGV[5])
                redis.call('EXPIRE', KEYS[2], ARGV[5])
                return {tonumber(redis.call('HGET', KEYS[1], 'uploadedBytes')),
                        tonumber(redis.call('HGET', KEYS[1], 'uploadedPartCount')),
                        tonumber(redis.call('HGET', KEYS[1], 'version'))}
            end
            local oldSize = 0
            if old then
                oldSize = tonumber(string.match(old, '^(%d+):')) or 0
            else
                redis.call('HINCRBY', KEYS[1], 'uploadedPartCount', 1)
            end
            redis.call('HSET', KEYS[2], ARGV[1], value)
            redis.call('HINCRBY', KEYS[1], 'uploadedBytes', tonumber(ARGV[2]) - oldSize)
            redis.call('HINCRBY', KEYS[1], 'version', 1)
            redis.call('HSET', KEYS[1], 'updatedAt', ARGV[4])
            redis.call('EXPIRE', KEYS[1], ARGV[5])
            redis.call('EXPIRE', KEYS[2], ARGV[5])
            return {tonumber(redis.call('HGET', KEYS[1], 'uploadedBytes')),
                    tonumber(redis.call('HGET', KEYS[1], 'uploadedPartCount')),
                    tonumber(redis.call('HGET', KEYS[1], 'version'))}
            """, List.class);
    /**
     * 仅在 Redis 锁值仍属于本次持有者时删除，防止释放其他回源任务的锁。
     */
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    private final MediaProperties properties;
    private final MediaFileRepository fileRepository;
    private final MediaFileUploadRepository uploadRepository;
    private final FileObjectStorageService storage;
    private final StringRedisTemplate redis;
    private final Executor rebuildExecutor;

    /**
     * 初始化 FileUploadProgressService，保存所需依赖及初始运行状态。
     *
     * @param properties 服务配置
     * @param fileRepository 查询文件主记录，支持租户过滤、来源复用和保留期清理。
     * @param uploadRepository 访问上传会话及配额计数，以悲观锁和带所有者条件的更新维护合并租约。
     * @param storage 封装 MinIO 对象和分片读写、签名地址及桶初始化；调用方负责传入流的生命周期。
     * @param redis Redis 字符串访问器
     * @param rebuildExecutor 后台工作执行器
     */
    public FileUploadProgressService(
            MediaProperties properties,
            MediaFileRepository fileRepository,
            MediaFileUploadRepository uploadRepository,
            FileObjectStorageService storage,
            StringRedisTemplate redis,
            @Qualifier("fileProgressRebuildExecutor") Executor rebuildExecutor) {
        this.properties = properties;
        this.fileRepository = fileRepository;
        this.uploadRepository = uploadRepository;
        this.storage = storage;
        this.redis = redis;
        this.rebuildExecutor = rebuildExecutor;
    }

    /**
     * 按文件 ID 合并数据库与 Redis 进度，缓存缺失时触发回源；本层不校验用户权限，入口依赖内网隔离。
     * @param requestedFileIds 请求查询的文件 ID 集合
     * @return 进度条目、缺失文件 ID 和快照生成时间
     */
    public FileUploadProgressQueryResponse query(List<String> requestedFileIds) {
        LinkedHashSet<String> fileIds = normalizeFileIds(requestedFileIds);
        if (fileIds.isEmpty()) {
            return new FileUploadProgressQueryResponse(List.of(), List.of(), now());
        }

        Map<String, MediaFile> files = new LinkedHashMap<>();
        fileRepository.findAllById(fileIds).forEach(file -> files.put(file.getFileId(), file));
        Map<String, MediaFileUpload> latestUploads = latestUploads(files.keySet());

        List<String> missing = fileIds.stream().filter(fileId -> !files.containsKey(fileId)).toList();
        List<CompletableFuture<FileUploadProgressItem>> futures = new ArrayList<>();
        for (String fileId : fileIds) {
            MediaFile file = files.get(fileId);
            if (file == null) {
                continue;
            }
            MediaFileUpload upload = latestUploads.get(fileId);
            if (upload != null && (upload.getStatus() == FileUploadStatus.ACTIVE
                    || upload.getStatus() == FileUploadStatus.COMPLETING)) {
                futures.add(CompletableFuture.supplyAsync(() -> activeItem(file, upload), rebuildExecutor));
            } else {
                futures.add(CompletableFuture.completedFuture(databaseItem(file, upload)));
            }
        }
        List<FileUploadProgressItem> items = futures.stream().map(CompletableFuture::join).toList();
        return new FileUploadProgressQueryResponse(items, missing, now());
    }

    /**
     * 合并 MinIO 分片事件到进度缓存；对重复分片以最新可用事实重算。
     *
     * @param payload 消息载荷
     */
    public void acceptMinioEvent(JsonNode payload) {
        if (!properties.getFile().isProgressEnabled()) {
            return;
        }
        JsonNode records = payload == null ? null : payload.path("Records");
        if (records == null || !records.isArray()) {
            throw new FileApiException(HttpStatus.BAD_REQUEST, "INVALID_PART_EVENT", "MinIO 事件缺少 Records");
        }
        for (JsonNode record : records) {
            String eventName = record.path("eventName").asText("");
            if (!eventName.startsWith("s3:ObjectCreated:")) {
                continue;
            }
            String encodedKey = record.path("s3").path("object").path("key").asText("");
            Matcher matcher = PART_KEY.matcher(URLDecoder.decode(encodedKey, StandardCharsets.UTF_8));
            if (!matcher.matches()) {
                continue;
            }
            String storageUploadId = matcher.group(1);
            int partNumber;
            try {
                partNumber = Integer.parseInt(matcher.group(2));
            } catch (NumberFormatException exception) {
                continue;
            }
            long size = record.path("s3").path("object").path("size").asLong(-1L);
            if (size < 0) {
                throw new FileApiException(HttpStatus.BAD_REQUEST, "INVALID_PART_EVENT", "MinIO 分片事件缺少对象大小");
            }
            String etag = record.path("s3").path("object").path("eTag").asText("");
            UploadContext context = context(storageUploadId);
            if (context == null || partNumber < 1 || partNumber > context.partCount()) {
                continue;
            }
            try {
                recordPart(context, partNumber, size, etag);
            } catch (DataAccessException exception) {
                throw new FileApiException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "PROGRESS_STORE_UNAVAILABLE",
                        "上传进度存储暂时不可用",
                        true,
                        Map.of());
            }
        }
    }

    /**
     * 结合上传上下文与缓存分片事实组装活动进度；重建并发受限，未知字节数不当成已完成。
     */
    private FileUploadProgressItem activeItem(MediaFile file, MediaFileUpload upload) {
        UploadContext context = context(file, upload);
        boolean redisAvailable = true;
        try {
            FileUploadProgressItem cached = cachedItem(file, upload);
            if (cached != null) {
                return cached;
            }
        } catch (DataAccessException exception) {
            redisAvailable = false;
            log.warn("读取上传进度缓存失败，回源 MinIO: 文件标识={}, 上传标识={}", file.getFileId(), upload.getUploadId());
        }

        String lockToken = null;
        if (redisAvailable) {
            try {
                lockToken = UUID.randomUUID().toString();
                Boolean acquired = redis.opsForValue().setIfAbsent(
                        rebuildLockKey(upload.getUploadId()),
                        lockToken,
                        Duration.ofSeconds(10));
                if (!Boolean.TRUE.equals(acquired)) {
                    return item(
                            file,
                            upload,
                            FileProgressPhase.UNKNOWN,
                            0,
                            0,
                            0,
                            now(),
                            "PROGRESS_REBUILD_PENDING",
                            "上传进度正在重建");
                }
            } catch (DataAccessException exception) {
                redisAvailable = false;
                lockToken = null;
            }
        }
        try {
            List<FileObjectStorageService.StoredPart> parts = storage.listParts(file.getObjectKey(), upload.getStorageUploadId());
            long uploadedBytes = 0;
            for (FileObjectStorageService.StoredPart part : parts) {
                uploadedBytes += part.size();
                try {
                    recordPart(context, part.partNumber(), part.size(), part.etag());
                } catch (DataAccessException exception) {
                    // Redis 故障时仍以本次 MinIO 事实返回，不让进度查询影响上传主链路。
                }
            }
            FileProgressPhase phase = upload.getStatus() == FileUploadStatus.COMPLETING
                    ? FileProgressPhase.FINALIZING
                    : FileProgressPhase.UPLOADING;
            return item(file, upload, phase, uploadedBytes, parts.size(), 0L, now(), null, null);
        } catch (RuntimeException exception) {
            log.warn("从 MinIO 重建上传进度失败: 文件标识={}, 上传标识={}", file.getFileId(), upload.getUploadId(), exception);
            return item(
                    file,
                    upload,
                    FileProgressPhase.UNKNOWN,
                    0,
                    0,
                    0,
                    now(),
                    "PROGRESS_REBUILD_FAILED",
                    "上传进度暂时不可用");
        } finally {
            if (lockToken != null) {
                try {
                    redis.execute(
                            RELEASE_LOCK_SCRIPT,
                            List.of(rebuildLockKey(upload.getUploadId())),
                            lockToken);
                } catch (DataAccessException exception) {
                    log.debug("释放上传进度重建锁失败: 上传标识={}", upload.getUploadId());
                }
            }
        }
    }

    private FileUploadProgressItem cachedItem(MediaFile file, MediaFileUpload upload) {
        Map<Object, Object> values = redis.opsForHash().entries(summaryKey(upload.getUploadId()));
        if (values.isEmpty()) {
            return null;
        }
        long uploadedBytes = longValue(values.get("uploadedBytes"));
        int uploadedPartCount = (int) longValue(values.get("uploadedPartCount"));
        long version = longValue(values.get("version"));
        OffsetDateTime updatedAt = dateTimeValue(values.get("updatedAt"), upload.getLastActiveAt());
        FileProgressPhase phase = upload.getStatus() == FileUploadStatus.COMPLETING
                ? FileProgressPhase.FINALIZING
                : FileProgressPhase.UPLOADING;
        return item(file, upload, phase, uploadedBytes, uploadedPartCount, version, updatedAt, null, null);
    }

    /**
     * 以数据库终态为准生成进度；已上传但后处理失败仍保留完整上传字节数，未完成上传不伪装为满进度。
     */
    private FileUploadProgressItem databaseItem(MediaFile file, MediaFileUpload upload) {
        FileProgressPhase phase = phase(file, upload);
        boolean fullyUploaded = file.getStatus() == FileStatus.READY
                || file.getStatus() == FileStatus.PROCESSING
                || file.getUploadedAt() != null
                || upload != null && upload.getStatus() == FileUploadStatus.COMPLETED;
        long uploadedBytes = fullyUploaded ? file.getFileSize() : 0L;
        int uploadedParts = fullyUploaded && upload != null && upload.getPartCount() != null
                ? upload.getPartCount()
                : 0;
        return item(
                file,
                upload,
                phase,
                uploadedBytes,
                uploadedParts,
                TERMINAL_VERSION,
                file.getUpdatedAt(),
                file.getErrorCode(),
                file.getErrorMessage());
    }

    private FileUploadProgressItem item(
            MediaFile file,
            MediaFileUpload upload,
            FileProgressPhase phase,
            long uploadedBytes,
            int uploadedPartCount,
            long version,
            OffsetDateTime updatedAt,
            String errorCode,
            String errorMessage) {
        long boundedBytes = Math.max(0, Math.min(uploadedBytes, file.getFileSize()));
        return new FileUploadProgressItem(
                file.getFileId(),
                upload == null ? null : upload.getUploadId(),
                file.getFileName(),
                file.getFileType().name(),
                phase,
                boundedBytes,
                file.getFileSize(),
                uploadedPartCount,
                upload == null ? null : upload.getPartCount(),
                percent(boundedBytes, file.getFileSize()),
                phase == FileProgressPhase.READY,
                version,
                updatedAt == null ? file.getCreatedAt() : updatedAt,
                errorCode,
                errorMessage);
    }

    private FileProgressPhase phase(MediaFile file, MediaFileUpload upload) {
        if (file.getStatus() == FileStatus.DELETED) {
            return FileProgressPhase.DELETED;
        }
        if (file.getStatus() == FileStatus.READY) {
            return FileProgressPhase.READY;
        }
        if (file.getStatus() == FileStatus.PROCESSING) {
            return FileProgressPhase.PROCESSING;
        }
        if (file.getStatus() == FileStatus.FAILED) {
            if (upload != null && upload.getStatus() == FileUploadStatus.EXPIRED) {
                return FileProgressPhase.EXPIRED;
            }
            if (upload != null && upload.getStatus() == FileUploadStatus.ABORTED) {
                return FileProgressPhase.ABORTED;
            }
            return FileProgressPhase.FAILED;
        }
        if (upload != null && upload.getStatus() == FileUploadStatus.COMPLETING) {
            return FileProgressPhase.FINALIZING;
        }
        return FileProgressPhase.UPLOADING;
    }

    /**
     * 通过 Redis 脚本原子登记分片及汇总字节数，重复通知按分片键更新而不重复累加。
     */
    private void recordPart(UploadContext context, int partNumber, long size, String etag) {
        long ttl = Math.max(
                60L,
                Duration.between(now(), context.expiresAt().plusHours(24)).getSeconds());
        redis.execute(
                RECORD_PART_SCRIPT,
                List.of(summaryKey(context.uploadId()), partsKey(context.uploadId())),
                String.valueOf(partNumber),
                String.valueOf(size),
                etag == null ? "" : etag,
                now().toString(),
                String.valueOf(ttl),
                context.fileId(),
                context.uploadId(),
                context.fileName(),
                context.fileType(),
                String.valueOf(context.totalBytes()),
                String.valueOf(context.partCount()));
        redis.opsForValue().set(storageKey(context.storageUploadId()), context.uploadId(), Duration.ofSeconds(ttl));
    }

    private UploadContext context(String storageUploadId) {
        try {
            String cachedUploadId = redis.opsForValue().get(storageKey(storageUploadId));
            if (cachedUploadId != null) {
                Map<Object, Object> summary = redis.opsForHash().entries(summaryKey(cachedUploadId));
                if (!summary.isEmpty()) {
                    return new UploadContext(
                            stringValue(summary.get("fileId")),
                            cachedUploadId,
                            storageUploadId,
                            stringValue(summary.get("fileName")),
                            stringValue(summary.get("fileType")),
                            longValue(summary.get("totalBytes")),
                            (int) longValue(summary.get("partCount")),
                            now().plusHours(properties.getFile().getMultipartExpireHours()));
                }
            }
        } catch (DataAccessException exception) {
            throw new FileApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "PROGRESS_STORE_UNAVAILABLE",
                    "上传进度存储暂时不可用",
                    true,
                    Map.of());
        }
        MediaFileUpload upload = uploadRepository.findByStorageUploadId(storageUploadId).orElse(null);
        if (upload == null || (upload.getStatus() != FileUploadStatus.ACTIVE
                && upload.getStatus() != FileUploadStatus.COMPLETING)) {
            return null;
        }
        MediaFile file = fileRepository.findById(upload.getFileId()).orElse(null);
        return file == null ? null : context(file, upload);
    }

    private UploadContext context(MediaFile file, MediaFileUpload upload) {
        return new UploadContext(
                file.getFileId(),
                upload.getUploadId(),
                upload.getStorageUploadId(),
                file.getFileName(),
                file.getFileType().name(),
                file.getFileSize(),
                upload.getPartCount(),
                upload.getExpiresAt());
    }

    private Map<String, MediaFileUpload> latestUploads(Collection<String> fileIds) {
        Map<String, MediaFileUpload> result = new LinkedHashMap<>();
        if (fileIds.isEmpty()) {
            return result;
        }
        for (MediaFileUpload upload : uploadRepository.findByFileIdInOrderByCreatedAtDesc(fileIds)) {
            result.putIfAbsent(upload.getFileId(), upload);
        }
        return result;
    }

    private LinkedHashSet<String> normalizeFileIds(List<String> requested) {
        LinkedHashSet<String> fileIds = new LinkedHashSet<>();
        if (requested == null) {
            return fileIds;
        }
        for (String fileId : requested) {
            if (fileId != null && !fileId.isBlank()) {
                fileIds.add(fileId.trim());
            }
        }
        return fileIds;
    }

    private double percent(long uploadedBytes, long totalBytes) {
        if (totalBytes <= 0) {
            return 0.0d;
        }
        return Math.floor((double) uploadedBytes * 10000.0d / (double) totalBytes) / 100.0d;
    }

    private long longValue(Object value) {
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private OffsetDateTime dateTimeValue(Object value, OffsetDateTime fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return OffsetDateTime.parse(String.valueOf(value));
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private String summaryKey(String uploadId) {
        return SUMMARY_PREFIX + uploadId + ":summary";
    }

    private String partsKey(String uploadId) {
        return SUMMARY_PREFIX + uploadId + ":parts";
    }

    private String storageKey(String storageUploadId) {
        return STORAGE_PREFIX + storageUploadId;
    }

    private String rebuildLockKey(String uploadId) {
        return SUMMARY_PREFIX + uploadId + ":rebuild-lock";
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    /**
     * 一次进度处理所需的上传会话快照，用于定位 Redis 键及校验分片范围。
     * @param fileId 文件 ID
     * @param uploadId 平台上传会话 ID
     * @param storageUploadId 平台生成的暂存分片会话标识，用于组织分片对象键
     * @param fileName 原始文件名
     * @param fileType 文件类型
     * @param totalBytes 预期文件总字节数
     * @param partCount 上传会话登记的分片总数
     * @param expiresAt 有效期截止时间
     */
    private record UploadContext(
            String fileId,
            String uploadId,
            String storageUploadId,
            String fileName,
            String fileType,
            long totalBytes,
            int partCount,
            OffsetDateTime expiresAt) {
    }
}
