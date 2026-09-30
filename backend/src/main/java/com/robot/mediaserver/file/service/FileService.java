package com.robot.mediaserver.file.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.mediaserver.auth.CurrentUser;
import com.robot.mediaserver.config.MediaProperties;
import com.robot.mediaserver.file.api.FileApiException;
import com.robot.mediaserver.file.dto.CreateMultipartFileUploadRequest;
import com.robot.media.common.file.FileBatchDeleteResponse;
import com.robot.media.common.file.FileDeleteResultResponse;
import com.robot.media.common.file.FileDownloadUrlResponse;
import com.robot.media.common.file.FileListItemResponse;
import com.robot.media.common.file.FileListResponse;
import com.robot.mediaserver.file.dto.FilePartInfoResponse;
import com.robot.mediaserver.file.dto.FilePartUploadUrlResponse;
import com.robot.mediaserver.file.dto.FilePartUrlsResponse;
import com.robot.media.common.file.FilePlayUrlResponse;
import com.robot.mediaserver.file.dto.FileStatusResponse;
import com.robot.mediaserver.file.dto.FileUploadResponse;
import com.robot.media.common.file.FileStatus;
import com.robot.media.common.file.FileType;
import com.robot.mediaserver.file.model.FileUploadMode;
import com.robot.mediaserver.file.model.FileUploadStatus;
import com.robot.mediaserver.file.model.MediaFile;
import com.robot.mediaserver.file.model.MediaFileUpload;
import com.robot.mediaserver.file.model.MediaVideoFile;
import com.robot.mediaserver.file.model.VideoFileStatus;
import com.robot.mediaserver.file.repository.MediaFileRepository;
import com.robot.mediaserver.file.repository.MediaFileUploadRepository;
import com.robot.mediaserver.file.repository.MediaVideoFileRepository;
import com.robot.mediaserver.livekit.LiveKitEgressService;
import com.robot.mediaserver.video.model.VideoSession;
import jakarta.transaction.Transactional;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** 管理文件元数据、上传会话、所有权与播放授权，协调存储及视频处理状态。 */
@Service
public class FileService {

    /**
     * 单个文件或播放分片允许通过服务内存代理读取的最大字节数。
     */
    private static final long MAX_PROXIED_FILE_BYTES = 32L * 1024 * 1024;

    private static final Logger log = LoggerFactory.getLogger(FileService.class);
    private static final String MEDIA_VIEWER = "MEDIA_VIEWER";
    private static final String MEDIA_OPERATOR = "MEDIA_OPERATOR";
    /**
     * 按创建用户限制可见性的文件来源标识；普通设备文件仍按组织范围管理。
     */
    private static final Set<String> USER_OWNED_SOURCES = Set.of(
            "WEB_SNAPSHOT", "LIVEKIT_EGRESS");

    private final MediaProperties properties;
    private final MediaFileRepository fileRepository;
    private final MediaFileUploadRepository uploadRepository;
    private final MediaVideoFileRepository videoRepository;
    private final FileObjectStorageService storage;
    private final LiveKitEgressService egressService;
    private final ObjectMapper objectMapper;
    private final FileSourceLockService sourceLockService;

    /**
     * 初始化 FileService，保存所需依赖及初始运行状态。
     *
     * @param properties 服务配置
     * @param fileRepository 查询文件主记录，支持租户过滤、来源复用和保留期清理。
     * @param uploadRepository 访问上传会话及配额计数，以悲观锁和带所有者条件的更新维护合并租约。
     * @param videoRepository 按文件 ID 存取视频探测、HLS 处理状态及播放资源位置。
     * @param storage 封装 MinIO 对象和分片读写、签名地址及桶初始化；调用方负责传入流的生命周期。
     * @param egressService 调用 LiveKit Egress 启停录制，并返回外部任务标识和状态。
     * @param objectMapper JSON 编解码器
     * @param sourceLockService 通过持久化互斥行防止同一机器人来源文件并发创建重复上传任务。
     */
    public FileService(
            MediaProperties properties,
            MediaFileRepository fileRepository,
            MediaFileUploadRepository uploadRepository,
            MediaVideoFileRepository videoRepository,
            FileObjectStorageService storage,
            LiveKitEgressService egressService,
            ObjectMapper objectMapper,
            FileSourceLockService sourceLockService) {
        this.properties = properties;
        this.fileRepository = fileRepository;
        this.uploadRepository = uploadRepository;
        this.videoRepository = videoRepository;
        this.storage = storage;
        this.egressService = egressService;
        this.objectMapper = objectMapper;
        this.sourceLockService = sourceLockService;
    }

    /**
     * 校验上传配额与来源后保存文件；上传流在成功和失败路径均关闭。
     *
     * @param user 当前用户
     * @param file 上传文件
     * @param fileType 文件类型
     * @param robotId 机器人 ID
     * @param deviceId 设备 ID
     * @param extensionId 通用扩展 ID
     * @param sourceFileId 源文件 ID
     * @param metadata 扩展元数据
     * @return 文件 ID、上传状态及后续处理信息
     */
    @Transactional
    public FileListItemResponse uploadSimple(
            CurrentUser user,
            MultipartFile file,
            FileType fileType,
            String robotId,
            String deviceId,
            String extensionId,
            String sourceFileId,
            String metadata) {
        if (file == null || file.isEmpty()) {
            throw error(HttpStatus.BAD_REQUEST, "FILE_EMPTY", "文件不能为空");
        }
        if (file.getSize() > properties.getFile().getSimpleUploadMaxBytes()) {
            throw error(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE_USE_MULTIPART", "文件超过单接口上传大小限制");
        }
        OffsetDateTime timestamp = now();
        MediaFile entity = newFile(
                user == null ? properties.getFile().getDefaultOrgId() : user.orgId(),
                robotId,
                deviceId,
                extensionId,
                sourceFileId,
                fileType,
                file.getOriginalFilename() == null ? "file" : file.getOriginalFilename(),
                file.getContentType() == null ? "application/octet-stream" : file.getContentType(),
                file.getSize(),
                FileUploadMode.SIMPLE,
                metadata,
                timestamp);
        if (isUserOwnedSource(metadata)) {
            requireRole(user, MEDIA_OPERATOR, "无手动媒体操作权限");
            entity.setCreatedBy(user.userId());
        }
        fileRepository.save(entity);
        try (InputStream input = file.getInputStream()) {
            storage.upload(entity.getObjectKey(), input, file.getSize(), entity.getContentType());
        } catch (FileStorageException ex) {
            markSimpleUploadFailed(entity, "STORAGE_UNAVAILABLE", ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            markSimpleUploadFailed(entity, "SIMPLE_UPLOAD_FAILED", ex.getMessage());
            throw error(HttpStatus.CONFLICT, "SIMPLE_UPLOAD_FAILED", ex.getMessage());
        }
        markUploaded(entity);
        return item(entity);
    }

    /**
     * 按来源文件标识创建或恢复分片会话，避免同源重复上传。
     *
     * @param robotIdHeader 可信机器人标识请求头
     * @param request 请求参数
     * @return 会话标识、分片参数及首批预签名地址
     */
    @Transactional
    public FileUploadResponse createOrResumeMultipart(String robotIdHeader, CreateMultipartFileUploadRequest request) {
        String robotId = requiredRobotId(robotIdHeader);
        if (request.getRobotId() != null
                && !request.getRobotId().isBlank()
                && !robotId.equals(request.getRobotId().trim())) {
            throw error(HttpStatus.FORBIDDEN, "ROBOT_ID_MISMATCH", "请求体机器人标识与请求头不一致");
        }
        if (request.getFileSize() > properties.getFile().getMaxFileSizeBytes()) {
            throw error(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "文件超过配置的大小限制");
        }
        MediaFile file = null;
        if (robotId != null && request.getSourceFileId() != null && !request.getSourceFileId().isBlank()) {
            sourceLockService.lock(robotId, request.getSourceFileId());
            file = fileRepository.findByRobotIdAndSourceFileId(robotId, request.getSourceFileId()).orElse(null);
        }
        if (file != null) {
            ensureSameSource(file, request);
            if (file.getStatus() == FileStatus.READY || file.getStatus() == FileStatus.PROCESSING) {
                return completedResponse(file);
            }
            MediaFileUpload active = uploadRepository
                    .findFirstByFileIdAndStatusOrderByCreatedAtDesc(file.getFileId(), FileUploadStatus.ACTIVE)
                    .orElse(null);
            if (active != null && active.getExpiresAt().isAfter(now())) {
                refresh(active);
                return uploadResponse(file, active, initialPartNumbers(active));
            }
        } else {
            file = newFile(
                    properties.getFile().getDefaultOrgId(),
                    robotId,
                    request.getDeviceId(),
                    request.getExtensionId(),
                    request.getSourceFileId(),
                    request.getFileType(),
                    request.getFileName(),
                    request.getContentType(),
                    request.getFileSize(),
                    FileUploadMode.MULTIPART,
                    request.getMetadata(),
                    now());
            fileRepository.save(file);
            if (file.getFileType() == FileType.VIDEO) {
                ensureVideo(file, VideoFileStatus.PROCESSING);
            }
        }
        checkQuota(robotId);
        MediaFileUpload upload = newUpload(file);
        file.setStatus(FileStatus.UPLOADING);
        file.setUpdatedAt(now());
        fileRepository.save(file);
        uploadRepository.save(upload);
        return uploadResponse(file, upload, initialPartNumbers(upload));
    }

    /**
     * 核对上传会话和分片范围后补签上传地址。
     *
     * @param robotId 机器人 ID
     * @param uploadId 平台上传会话 ID
     * @param partNumbers 要签名的分片编号；必须非空，服务校验范围、数量并去重
     * @return 本次签发的分片地址及有效期
     */
    @Transactional
    public FilePartUrlsResponse partUrls(String robotId, String uploadId, List<Integer> partNumbers) {
        MediaFileUpload upload = requireActiveUpload(requiredRobotId(robotId), uploadId);
        int maxPartUrls = Math.max(1, properties.getFile().getMaxPartUrlsPerRequest());
        if (partNumbers.size() > maxPartUrls) {
            throw error(HttpStatus.BAD_REQUEST, "TOO_MANY_PART_URLS", "一次请求的分片地址过多");
        }
        validatePartNumbers(upload, partNumbers);
        refresh(upload);
        MediaFile file = requireFile(upload.getFileId());
        List<FilePartUploadUrlResponse> urls = partNumbers.stream()
                .distinct()
                .map(number -> new FilePartUploadUrlResponse(
                        number,
                        storage.presignUploadPart(file.getObjectKey(), upload.getStorageUploadId(), number)))
                .toList();
        return new FilePartUrlsResponse(now().plusSeconds(properties.getFile().getUploadUrlTtlSeconds()), urls);
    }

    /**
     * 读取文件当前状态和大小，区分上传完成与后处理就绪。
     *
     * @param robotId 机器人 ID
     * @param fileId 文件 ID
     * @return 文件状态、就绪标志及失败原因
     */
    public FileStatusResponse fileStatus(String robotId, String fileId) {
        robotId = requiredRobotId(robotId);
        MediaFile file = requireFile(fileId);
        if (!Objects.equals(file.getRobotId(), robotId)) {
            throw error(HttpStatus.NOT_FOUND, "FILE_NOT_FOUND", "未找到文件");
        }
        return status(file);
    }

    private String requiredRobotId(String robotId) {
        if (robotId == null || robotId.isBlank()) {
            throw error(HttpStatus.UNAUTHORIZED, "ROBOT_ID_REQUIRED", "机器人上传请求缺少 X-Robot-Id");
        }
        return robotId.trim();
    }

    /**
     * 在当前用户可见范围内按筛选条件分页查询文件。
     *
     * @param user 当前用户
     * @param robotId 机器人 ID
     * @param deviceId 设备 ID
     * @param extensionId 通用扩展 ID
     * @param fileType 文件类型
     * @param status 当前业务状态，取值遵循所属模型的状态协议
     * @param source 文件来源，对应 metadata.source
     * @param page 页码
     * @param size 分页大小
     * @return 文件列表和分页信息
     */
    public FileListResponse list(
            CurrentUser user,
            String robotId,
            String deviceId,
            String extensionId,
            FileType fileType,
            FileStatus status,
            String source,
            int page,
            int size) {
        Specification<MediaFile> spec = (root, query, cb) -> cb.equal(root.get("orgId"), user.orgId());
        spec = spec.and((root, query, cb) -> canViewUserOwnedFiles(user)
                ? cb.or(cb.isNull(root.get("createdBy")), cb.equal(root.get("createdBy"), user.userId()))
                : cb.isNull(root.get("createdBy")));
        if (robotId != null && !robotId.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("robotId"), robotId));
        }
        if (deviceId != null && !deviceId.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("deviceId"), deviceId));
        }
        if (extensionId != null && !extensionId.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("extensionId"), extensionId));
        }
        if (fileType != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("fileType"), fileType));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (source != null && !source.isBlank()) {
            String normalizedSource = source.trim();
            spec = spec.and((root, query, cb) -> cb.equal(
                    cb.function(
                            "json_unquote",
                            String.class,
                            cb.function("json_extract", String.class, root.get("metadataJson"), cb.literal("$.source"))),
                    normalizedSource));
        }
        Page<MediaFile> result = fileRepository.findAll(
                spec,
                PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)), Sort.by(Sort.Direction.DESC, "createdAt")));
        return new FileListResponse(result.stream().map(this::item).toList(), result.getNumber(), result.getSize(), result.getTotalElements());
    }

    /**
     * 为当前用户可操作的文件绑定业务扩展标识。
     *
     * @param user 当前用户
     * @param request 请求参数
     */
    @Transactional
    public void bindExtension(CurrentUser user, Map<String, Object> request) {
        String extensionId = stringValue(request.get("extensionId"));
        if (extensionId == null || extensionId.isBlank()) {
            throw error(HttpStatus.BAD_REQUEST, "EXTENSION_ID_REQUIRED", "通用扩展 ID 不能为空");
        }
        List<String> genericFileIds = stringList(request.get("fileIds"));
        List<String> videoFileIds = stringList(request.get("videoFileIds"));
        String pointFileId = stringValue(request.get("pointFileId"));

        LinkedHashSet<String> fileIds = new LinkedHashSet<>();
        genericFileIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .forEach(fileIds::add);
        videoFileIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .forEach(fileIds::add);
        if (pointFileId != null && !pointFileId.isBlank()) {
            fileIds.add(pointFileId);
        }
        if (fileIds.isEmpty()) {
            throw error(HttpStatus.BAD_REQUEST, "FILE_IDS_EMPTY", "文件 ID 不能为空");
        }

        List<MediaFile> files = new ArrayList<>();
        for (String fileId : fileIds) {
            MediaFile file = requireFile(fileId);
            requireFileAccess(user, file, MEDIA_OPERATOR, "FILE_NOT_FOUND", "未找到文件");
            files.add(file);
        }

        for (String videoFileId : videoFileIds) {
            if (videoFileId == null || videoFileId.isBlank()) {
                continue;
            }
            MediaFile video = files.stream()
                    .filter(file -> Objects.equals(file.getFileId(), videoFileId))
                    .findFirst()
                    .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "FILE_NOT_FOUND", "未找到文件"));
            if (video.getFileType() != FileType.VIDEO) {
                throw error(HttpStatus.BAD_REQUEST, "FILE_TYPE_MISMATCH", "视频文件 ID 对应的文件类型不是 VIDEO");
            }
        }

        if (pointFileId != null && !pointFileId.isBlank()) {
            MediaFile pointFile = files.stream()
                    .filter(file -> Objects.equals(file.getFileId(), pointFileId))
                    .findFirst()
                    .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "FILE_NOT_FOUND", "未找到文件"));
            if (pointFile.getFileType() == FileType.VIDEO) {
                throw error(HttpStatus.BAD_REQUEST, "FILE_TYPE_MISMATCH", "点位文件不能是 VIDEO");
            }
        }

        OffsetDateTime timestamp = now();
        for (MediaFile file : files) {
            file.setExtensionId(extensionId);
            file.setUpdatedAt(timestamp);
        }
        fileRepository.saveAll(files);
    }

    /**
     * 校验文件访问权限后返回明细。
     *
     * @param user 当前用户
     * @param fileId 文件 ID
     * @return 文件明细；不存在或无权访问时抛出业务异常
     */
    public FileListItemResponse detail(CurrentUser user, String fileId) {
        MediaFile file = requireFile(fileId);
        requireFileAccess(user, file, MEDIA_VIEWER, "FILE_NOT_FOUND", "未找到文件");
        return item(file);
    }

    /**
     * 校验删除权限并清理指定文件及关联存储资产。
     *
     * @param user 当前用户
     * @param fileId 文件 ID
     */
    @Transactional
    public void delete(CurrentUser user, String fileId) {
        MediaFile file = requireFile(fileId);
        requireFileAccess(user, file, MEDIA_OPERATOR, "FILE_NOT_FOUND", "未找到文件");
        if (file.getStatus() == FileStatus.DELETED) {
            return;
        }

        uploadRepository
                .findFirstByFileIdAndStatusOrderByCreatedAtDesc(fileId, FileUploadStatus.ACTIVE)
                .ifPresent(upload -> {
                    storage.abortMultipart(file.getObjectKey(), upload.getStorageUploadId());
                    upload.setStatus(FileUploadStatus.ABORTED);
                    upload.setLastActiveAt(now());
                    uploadRepository.save(upload);
                });
        deleteFileAssets(file);
    }

    /**
     * 逐项执行文件删除并保留单项失败，避免一个失败掩盖其他结果。
     * @param user 当前用户
     * @param fileIds 待逐项删除的通用文件 ID 列表
     * @return 成功与失败的逐文件删除结果
     */
    public FileBatchDeleteResponse deleteBatch(CurrentUser user, List<String> fileIds) {
        List<FileDeleteResultResponse> results = new ArrayList<>(fileIds.size());
        int succeeded = 0;
        for (String fileId : fileIds) {
            try {
                delete(user, fileId);
                results.add(new FileDeleteResultResponse(fileId, true, "DELETED", "删除成功"));
                succeeded++;
            } catch (FileApiException ex) {
                results.add(new FileDeleteResultResponse(fileId, false, ex.getCode(), ex.getMessage()));
            } catch (RuntimeException ex) {
                log.warn("批量删除文件失败: 文件标识={}", fileId, ex);
                results.add(new FileDeleteResultResponse(fileId, false, "DELETE_FAILED", "删除失败"));
            }
        }
        return new FileBatchDeleteResponse(fileIds.size(), succeeded, fileIds.size() - succeeded, results);
    }

    /**
     * 校验访问权限并签发临时下载地址。
     *
     * @param user 当前用户
     * @param fileId 文件 ID
     * @param inline 是否使用内联展示的响应方式
     * @return 下载 URL、文件信息和到期时间
     */
    public FileDownloadUrlResponse downloadUrl(CurrentUser user, String fileId, boolean inline) {
        MediaFile file = requirePlayableFile(user, fileId);
        OffsetDateTime expiresAt = now().plusSeconds(properties.getFile().getPlayUrlTtlSeconds());
        return new FileDownloadUrlResponse(
                fileId,
                storage.presignDownload(
                        file.getObjectKey(),
                        properties.getFile().getPlayUrlTtlSeconds(),
                        file.getFileName(),
                        file.getContentType(),
                        inline),
                expiresAt);
    }

    /**
     * 按既有权限及大小上限读取文件正文，供兼容代理入口使用。
     *
     * @param user 当前用户
     * @param fileId 文件 ID
     * @return 文件正文的二进制字节
     */
    public PlaybackAsset content(CurrentUser user, String fileId) {
        MediaFile file = requirePlayableFile(user, fileId);
        requireProxySize(file.getObjectKey());
        return new PlaybackAsset(storage.readObject(file.getObjectKey()), file.getContentType());
    }

    /**
     * 根据文件类型与就绪状态生成可播放地址。
     *
     * @param user 当前用户
     * @param fileId 文件 ID
     * @return 播放类型、地址、有效期及可用状态
     */
    public FilePlayUrlResponse playUrl(CurrentUser user, String fileId) {
        MediaFile file = requirePlayableFile(user, fileId);
        if (file.getFileType() != FileType.VIDEO) {
            throw error(HttpStatus.BAD_REQUEST, "FILE_NOT_VIDEO", "文件不是视频");
        }
        MediaVideoFile video = videoRepository.findById(fileId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "VIDEO_NOT_READY", "视频尚未就绪"));
        if (video.getStatus() != VideoFileStatus.READY) {
            throw error(HttpStatus.NOT_FOUND, "VIDEO_NOT_READY", "视频尚未就绪");
        }
        OffsetDateTime expiresAt = now().plusSeconds(properties.getFile().getPlayUrlTtlSeconds());
        String token = signPlayback(fileId, expiresAt.toEpochSecond());
        String path = "/api/media/files/" + fileId + "/hls/" + playlistAssetName(file, video) + "?token="
                + URLEncoder.encode(token, StandardCharsets.UTF_8);
        return new FilePlayUrlResponse(fileId, "hls", "application/vnd.apple.mpegurl", path, expiresAt);
    }

    /**
     * 验证播放令牌与相对资产路径后读取 HLS 文件。
     *
     * @param fileId 文件 ID
     * @param objectName 对象名称
     * @param token 访问令牌
     * @return 播放资产字节及其媒体类型
     */
    public PlaybackAsset playbackAsset(String fileId, String objectName, String token) {
        verifyPlayback(fileId, token);
        if (!objectName.matches("[A-Za-z0-9_.-]+")) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_ASSET_NAME", "无效的 HLS 资源名称");
        }
        MediaFile file = requireFile(fileId);
        if (file.getStatus() != FileStatus.READY || file.getFileType() != FileType.VIDEO) {
            throw error(HttpStatus.NOT_FOUND, "VIDEO_NOT_READY", "视频尚未就绪");
        }
        String objectKey = storage.hlsPrefix(file.getObjectKey()) + objectName;
        requireProxySize(objectKey);
        byte[] bytes = storage.readObject(objectKey);
        if (objectName.endsWith(".m3u8")) {
            String playlist = new String(bytes, StandardCharsets.UTF_8);
            String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8);
            String rewritten = playlist.lines()
                    .map(line -> rewriteHlsLine(line, encodedToken))
                    .reduce("", (left, line) -> left + line + "\n");
            return new PlaybackAsset(rewritten.getBytes(StandardCharsets.UTF_8), "application/vnd.apple.mpegurl");
        }
        return new PlaybackAsset(bytes, hlsContentType(objectName));
    }

    /**
     * 创建手动录像记录并启动 LiveKit 导出；同一会话已有活动录像时返回冲突。
     * @param session 待录制的业务视频会话
     * @param liveKitTrackSid 要核验或操作的 LiveKit 轨道标识
     * @param user 当前用户
     * @return 活动录像的文件信息
     */
    @Transactional
    public synchronized FileListItemResponse startLiveRecording(
            VideoSession session, String liveKitTrackSid, CurrentUser user) {
        requireRole(user, MEDIA_OPERATOR, "无手动媒体操作权限");
        MediaFile active = findActiveLiveRecording(session.getSessionId(), null).orElse(null);
        if (active != null) {
            throw error(HttpStatus.CONFLICT, "RECORDING_ALREADY_ACTIVE", "当前视频正在录制中");
        }
        OffsetDateTime timestamp = now();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", "LIVEKIT_EGRESS");
        metadata.put("sessionId", session.getSessionId());
        metadata.put("roomName", session.getRoomName());
        metadata.put("trackSid", liveKitTrackSid);
        metadata.put("startedClientId", user.clientId());
        MediaFile file = newFile(
                user.orgId(),
                session.getRobotId(),
                session.getDeviceId(),
                null,
                "livekit-egress:" + session.getSessionId() + ":" + timestamp.toEpochSecond(),
                FileType.VIDEO,
                "%s-%s-%s.mp4".formatted(session.getRobotId(), session.getDeviceId(), timestamp.toEpochSecond()),
                "video/mp4",
                0,
                FileUploadMode.MULTIPART,
                writeMetadata(metadata),
                timestamp);
        file.setCreatedBy(user.userId());
        file.setStatus(FileStatus.UPLOADING);
        fileRepository.save(file);
        MediaVideoFile video = ensureVideo(file, VideoFileStatus.PROCESSING);
        video.setStartedAt(timestamp);
        videoRepository.save(video);
        try {
            LiveKitEgressService.EgressStartResult result = egressService.startTrackMp4(
                    session.getRoomName(), liveKitTrackSid, file.getObjectKey());
            metadata.put("egressId", result.egressId());
            metadata.put("egressStatus", result.status());
            file.setMetadataJson(writeMetadata(metadata));
            file.setUpdatedAt(now());
            fileRepository.save(file);
            return item(file);
        } catch (Exception ex) {
            file.setStatus(FileStatus.FAILED);
            file.setErrorCode("EGRESS_START_FAILED");
            file.setErrorMessage(ex.getMessage());
            file.setUpdatedAt(now());
            fileRepository.save(file);
            throw error(HttpStatus.CONFLICT, "EGRESS_START_FAILED", ex.getMessage());
        }
    }

    /**
     * 校验录像归属后停止 LiveKit 导出，保留后处理状态而不提前宣告就绪。
     *
     * @param sessionId 会话 ID
     * @param fileId 文件 ID
     * @param user 当前用户
     * @return 停止请求后的录像文件信息
     */
    @Transactional
    public FileListItemResponse stopLiveRecording(String sessionId, String fileId, CurrentUser user) {
        MediaFile file = requireFile(fileId);
        requireFileAccess(user, file, MEDIA_OPERATOR, "RECORDING_NOT_ACTIVE", "录像未在进行中");
        if (!liveMetadata(file, "sessionId").equals(sessionId)) {
            throw error(HttpStatus.NOT_FOUND, "RECORDING_NOT_ACTIVE", "录像未在进行中");
        }
        String startedClientId = liveMetadata(file, "startedClientId");
        if (!startedClientId.isBlank() && !Objects.equals(startedClientId, user.clientId())) {
            throw error(HttpStatus.CONFLICT, "RECORDING_STARTED_BY_OTHER_CLIENT", "当前录像由其他浏览器发起");
        }
        if (file.getStatus() != FileStatus.UPLOADING) {
            return item(file);
        }
        finishLiveRecording(file);
        return item(file);
    }

    /**
     * 使用独立事务停止指定观看者的直播录像：停止失败时文件状态仍可落库为 FAILED，
     *  且不会把调用方（如启动清理）的事务标记为只能回滚 导致进程退出。
     *
     * @param sessionId 会话 ID
     * @param userId 用户 ID
     * @param clientId 客户端 ID
     * @return 是否找到归属该用户和终端的活动录像并执行收口
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public boolean stopLiveRecordingForClient(String sessionId, String userId, String clientId) {
        if (userId == null || userId.isBlank() || clientId == null || clientId.isBlank()) {
            return false;
        }
        MediaFile active = findActiveLiveRecording(sessionId, null)
                .filter(file -> Objects.equals(file.getCreatedBy(), userId))
                .filter(file -> Objects.equals(liveMetadata(file, "startedClientId"), clientId))
                .orElse(null);
        if (active == null) {
            return false;
        }
        finishLiveRecording(active);
        return true;
    }

    /**
     * 媒体源撤销或删除时停止指定会话的活动录像，不依赖发起录像的浏览器身份。
     *
     * @param sessionId 会话 ID
     * @return 是否找到活动录像并执行收口
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public boolean stopActiveLiveRecordingForSession(String sessionId) {
        MediaFile active = findActiveLiveRecording(sessionId, null).orElse(null);
        if (active == null) {
            return false;
        }
        finishLiveRecording(active);
        return true;
    }

    /**
     * 查询当前用户在指定视频会话中的活动录像。
     *
     * @param sessionId 会话 ID
     * @param user 当前用户
     * @return 活动录像信息；不存在时返回 null
     */
    public FileListItemResponse activeLiveRecording(String sessionId, CurrentUser user) {
        requireRole(user, MEDIA_VIEWER, "无手动媒体查看权限");
        return findActiveLiveRecording(sessionId, user).map(this::item).orElse(null);
    }

    /**
     * 判断指定视频会话是否仍有 LiveKit Egress 录像占用。
     *
     * @param sessionId 会话 ID
     * @return 会话是否仍有活动录像记录
     */
    public boolean hasActiveLiveRecording(String sessionId) {
        return findActiveLiveRecording(sessionId, null).isPresent();
    }

    /**
     * 查找超过允许时长的活动录像，供调度器有界收口。
     *
     * @return 本轮待过期处理的录像 ID
     */
    public List<String> expiredLiveRecordingIds() {
        int maxDurationSeconds = properties.getFile().getLiveRecordingMaxDurationSeconds();
        if (maxDurationSeconds <= 0) {
            return List.of();
        }
        OffsetDateTime threshold = now().minusSeconds(maxDurationSeconds);
        return fileRepository
                .findTop100ByFileTypeAndStatusAndSourceFileIdStartingWithAndCreatedAtBeforeOrderByCreatedAtAsc(
                        FileType.VIDEO,
                        FileStatus.UPLOADING,
                        "livekit-egress:",
                        threshold)
                .stream()
                .map(MediaFile::getFileId)
                .toList();
    }

    /**
     * 按最长时长规则收口指定活动录像。
     *
     * @param fileId 文件 ID
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void expireLiveRecording(String fileId) {
        MediaFile file = requireFile(fileId);
        if (file.getStatus() != FileStatus.UPLOADING
                || file.getSourceFileId() == null
                || !file.getSourceFileId().startsWith("livekit-egress:")) {
            return;
        }
        finishLiveRecording(file);
    }

    /**
     * 终止已过期的分片上传并同步文件失败或过期状态。
     *
     * @param upload 当前分片上传会话及其处理状态
     */
    @Transactional
    public void expireUpload(MediaFileUpload upload) {
        if (upload.getStatus() != FileUploadStatus.ACTIVE) {
            return;
        }
        MediaFile file = requireFile(upload.getFileId());
        storage.abortMultipart(file.getObjectKey(), upload.getStorageUploadId());
        upload.setStatus(FileUploadStatus.EXPIRED);
        file.setStatus(FileStatus.FAILED);
        file.setErrorCode("UPLOAD_EXPIRED");
        file.setErrorMessage("Upload session expired");
        file.setUpdatedAt(now());
        uploadRepository.save(upload);
        fileRepository.save(file);
    }

    /**
     * 删除文件目录下的源对象及播放产物，并将文件记录标记为 DELETED；不删除上传会话记录。
     * @param file 待清理的持久化文件记录
     */
    @Transactional
    public void deleteFileAssets(MediaFile file) {
        if (file.getStatus() == FileStatus.DELETED) {
            return;
        }
        storage.deletePrefix(storage.fileRootPrefix(file.getObjectKey()));
        file.setStatus(FileStatus.DELETED);
        file.setUpdatedAt(now());
        fileRepository.save(file);
    }

    /**
     * 在 HLS 产物已经发布后保存编码、尺寸及播放元数据，并标记就绪。
     *
     * @param fileId 文件 ID
     * @param probe 媒体文件探测得到的编码、尺寸与时长
     * @param playlistKey HLS 播放列表的对象存储键
     * @param segmentCount 本次生成的 HLS 分片数量
     * @param totalSize 产物总大小，单位字节
     * @param sourceSize 源文件大小，单位字节
     */
    @Transactional
    public void markVideoReady(String fileId, VideoProbeResult probe, String playlistKey, int segmentCount, long totalSize, long sourceSize) {
        MediaFile file = requireFile(fileId);
        MediaVideoFile video = videoRepository.findById(fileId).orElseGet(() -> newVideo(file, VideoFileStatus.PROCESSING));
        file.setFileSize(sourceSize);
        file.setStatus(FileStatus.READY);
        file.setErrorCode(null);
        file.setErrorMessage(null);
        file.setUpdatedAt(now());
        video.setVideoCodec(probe.videoCodec());
        video.setAudioCodec(probe.audioCodec());
        video.setWidth(probe.width());
        video.setHeight(probe.height());
        // 与浏览器播放器一致按整秒向下取整，避免小数时长被向上取整后多出 1 秒
        video.setDurationSeconds((int) Math.floor(Math.max(0d, probe.durationSeconds())));
        alignVideoTimeRange(file, video);
        video.setHlsPlaylistObjectKey(playlistKey);
        video.setHlsSegmentCount(segmentCount);
        video.setHlsTotalSize(totalSize);
        video.setStatus(VideoFileStatus.READY);
        video.setErrorCode(null);
        video.setErrorMessage(null);
        video.setProcessingCompletedAt(now());
        fileRepository.save(file);
        videoRepository.save(video);
    }

    /**
     * 记录视频后处理失败原因并结束本轮处理状态。
     *
     * @param fileId 文件 ID
     * @param errorCode 错误码
     * @param message 消息内容
     */
    @Transactional
    public void markVideoFailed(String fileId, String errorCode, String message) {
        MediaFile file = requireFile(fileId);
        MediaVideoFile video = videoRepository.findById(fileId).orElseGet(() -> newVideo(file, VideoFileStatus.PROCESSING));
        file.setStatus(FileStatus.FAILED);
        file.setErrorCode(errorCode);
        file.setErrorMessage(truncate(message == null ? "HLS processing failed" : message));
        file.setUpdatedAt(now());
        video.setStatus(VideoFileStatus.FAILED);
        video.setErrorCode(errorCode);
        video.setErrorMessage(truncate(message == null ? "HLS processing failed" : message));
        fileRepository.save(file);
        videoRepository.save(video);
    }

    private void finishLiveRecording(MediaFile file) {
        String egressId = liveMetadata(file, "egressId");
        if (!egressId.isBlank()) {
            try {
                egressService.stop(egressId);
            } catch (Exception ex) {
                if (egressAlreadyStopped(ex)) {
                    if (storedSourceSize(file) > 0) {
                        completeStoppedRecording(file);
                        return;
                    }
                    markRecordingAborted(file, ex.getMessage());
                    return;
                }
                file.setStatus(FileStatus.FAILED);
                file.setErrorCode("EGRESS_STOP_FAILED");
                file.setErrorMessage(ex.getMessage());
                file.setUpdatedAt(now());
                fileRepository.save(file);
                throw error(HttpStatus.CONFLICT, "EGRESS_STOP_FAILED", ex.getMessage());
            }
        }
        completeStoppedRecording(file);
    }

    private void completeStoppedRecording(MediaFile file) {
        long sourceSize = storedSourceSize(file);
        if (sourceSize > 0) {
            file.setFileSize(sourceSize);
        }
        file.setUploadedAt(now());
        file.setStatus(FileStatus.PROCESSING);
        file.setErrorCode(null);
        file.setErrorMessage(null);
        file.setUpdatedAt(now());
        MediaVideoFile video = ensureVideo(file, VideoFileStatus.PROCESSING);
        if (video.getEndedAt() == null) {
            video.setEndedAt(file.getUploadedAt());
            videoRepository.save(video);
        }
        fileRepository.save(file);
    }

    private long storedSourceSize(MediaFile file) {
        try {
            return storage.statSize(file.getObjectKey());
        } catch (Exception ignored) {
            return 0;
        }
    }

    private void markRecordingAborted(MediaFile file, String message) {
        OffsetDateTime timestamp = now();
        file.setStatus(FileStatus.FAILED);
        file.setErrorCode("EGRESS_ABORTED");
        file.setErrorMessage(message);
        file.setUpdatedAt(timestamp);
        MediaVideoFile video = ensureVideo(file, VideoFileStatus.FAILED);
        video.setErrorCode("EGRESS_ABORTED");
        video.setErrorMessage(message);
        video.setEndedAt(video.getEndedAt() == null ? timestamp : video.getEndedAt());
        video.setProcessingCompletedAt(timestamp);
        videoRepository.save(video);
        fileRepository.save(file);
    }

    private boolean egressAlreadyStopped(Exception ex) {
        String message = String.valueOf(ex.getMessage()).toLowerCase(java.util.Locale.ROOT);
        return message.contains("egress_aborted")
                || message.contains("egress not found")
                || message.contains("egress does not exist")
                || message.contains("egress_not_found")
                || message.contains("cannot be stopped")
                || message.contains("failed_precondition");
    }

    private void markUploaded(MediaFile file) {
        file.setUploadedAt(now());
        file.setStatus(file.getFileType() == FileType.VIDEO ? FileStatus.PROCESSING : FileStatus.READY);
        file.setErrorCode(null);
        file.setErrorMessage(null);
        file.setUpdatedAt(now());
        fileRepository.save(file);
        if (file.getFileType() == FileType.VIDEO) {
            ensureVideo(file, VideoFileStatus.PROCESSING);
        }
    }

    /**
     * 分片合并成功后，在数据库收口事务中推进文件处理状态。
     *
     * @param fileId 文件 ID
     */
    @Transactional
    public void markMultipartUploaded(String fileId) {
        markUploaded(requireFile(fileId));
    }

    private MediaFile newFile(
            String orgId,
            String robotId,
            String deviceId,
            String extensionId,
            String sourceFileId,
            FileType fileType,
            String fileName,
            String contentType,
            long fileSize,
            FileUploadMode uploadMode,
            String metadata,
            OffsetDateTime timestamp) {
        MediaFile file = new MediaFile();
        file.setFileId("file_" + id());
        file.setOrgId(orgId);
        file.setRobotId(blankToNull(robotId));
        file.setDeviceId(blankToNull(deviceId));
        file.setExtensionId(blankToNull(extensionId));
        file.setSourceFileId(blankToNull(sourceFileId));
        file.setFileType(fileType);
        file.setFileName(fileName);
        file.setContentType(contentType);
        file.setFileSize(fileSize);
        file.setUploadMode(uploadMode);
        file.setStatus(FileStatus.UPLOADING);
        file.setMetadataJson(blankToNull(metadata));
        file.setCreatedAt(timestamp);
        file.setUpdatedAt(timestamp);
        file.setObjectKey(storage.buildObjectKey(orgId, robotId, file.getFileId(), videoSourceName(fileType, fileName), timestamp));
        return file;
    }

    private MediaFileUpload newUpload(MediaFile file) {
        OffsetDateTime timestamp = now();
        MediaFileUpload upload = new MediaFileUpload();
        upload.setUploadId("upl_" + id());
        upload.setFileId(file.getFileId());
        upload.setUploadMode(FileUploadMode.MULTIPART);
        upload.setStorageUploadId(storage.initiateMultipart());
        long partSize = calculatePartSize(file.getFileSize());
        upload.setPartSize(partSize);
        upload.setPartCount(Math.toIntExact(ceilDiv(file.getFileSize(), partSize)));
        upload.setStatus(FileUploadStatus.ACTIVE);
        upload.setCreatedAt(timestamp);
        upload.setLastActiveAt(timestamp);
        upload.setExpiresAt(timestamp.plusHours(properties.getFile().getMultipartExpireHours()));
        return upload;
    }

    private List<Integer> initialPartNumbers(MediaFileUpload upload) {
        int count = Math.min(upload.getPartCount(), Math.max(1, properties.getFile().getInitialPartUrlCount()));
        List<Integer> numbers = new ArrayList<>();
        for (int part = 1; part <= count; part++) {
            numbers.add(part);
        }
        return numbers;
    }

    private FileUploadResponse uploadResponse(MediaFile file, MediaFileUpload upload, List<Integer> initialPartNumbers) {
        List<FilePartInfoResponse> uploaded = storage.listParts(file.getObjectKey(), upload.getStorageUploadId()).stream()
                .map(part -> new FilePartInfoResponse(part.partNumber(), part.etag(), part.size()))
                .toList();
        List<Integer> alreadyUploaded = uploaded.stream().map(FilePartInfoResponse::partNumber).toList();
        List<Integer> toSign = initialPartNumbers.stream().filter(part -> !alreadyUploaded.contains(part)).toList();
        List<FilePartUploadUrlResponse> partUrls = toSign.isEmpty()
                ? List.of()
                : partUrls(file.getObjectKey(), upload, toSign);
        return new FileUploadResponse(
                file.getFileId(),
                upload.getUploadId(),
                upload.getUploadMode().name(),
                file.getStatus().name(),
                upload.getPartSize(),
                upload.getPartCount(),
                uploaded,
                partUrls,
                upload.getExpiresAt());
    }

    private List<FilePartUploadUrlResponse> partUrls(String objectKey, MediaFileUpload upload, List<Integer> partNumbers) {
        return partNumbers.stream()
                .map(number -> new FilePartUploadUrlResponse(
                        number,
                        storage.presignUploadPart(objectKey, upload.getStorageUploadId(), number)))
                .toList();
    }

    private FileUploadResponse completedResponse(MediaFile file) {
        return new FileUploadResponse(file.getFileId(), null, file.getUploadMode().name(), file.getStatus().name(), 0, 0, List.of(), List.of(), null);
    }

    private void validatePartNumbers(MediaFileUpload upload, List<Integer> partNumbers) {
        for (Integer part : partNumbers) {
            if (part == null || part < 1 || part > upload.getPartCount()) {
                throw error(HttpStatus.BAD_REQUEST, "INVALID_PART_NUMBER", "无效的分片编号");
            }
        }
    }

    private void ensureSameSource(MediaFile file, CreateMultipartFileUploadRequest request) {
        if (file.getFileSize() != request.getFileSize()
                || file.getFileType() != request.getFileType()
                || !Objects.equals(file.getFileName(), request.getFileName())) {
            throw error(HttpStatus.CONFLICT, "SOURCE_FILE_CHANGED", "源文件在创建上传任务后发生变化");
        }
    }

    private void checkQuota(String robotId) {
        OffsetDateTime timestamp = now();
        int globalLimit = properties.getFile().getMaxActiveUploadsGlobal();
        long globalCount = uploadRepository.countByStatusAndExpiresAtAfter(FileUploadStatus.ACTIVE, timestamp);
        if (globalCount >= globalLimit) {
            throw new FileApiException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "UPLOAD_SESSION_LIMIT",
                    "全局未完成上传会话数量达到上限，请稍后重试",
                    true,
                    Map.of("scope", "GLOBAL", "activeCount", globalCount, "limit", globalLimit));
        }
        if (robotId != null) {
            int robotLimit = properties.getFile().getMaxActiveUploadsPerRobot();
            long robotCount = uploadRepository.countActiveByRobotId(
                    robotId,
                    FileUploadStatus.ACTIVE,
                    timestamp);
            if (robotCount >= robotLimit) {
                throw new FileApiException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "UPLOAD_SESSION_LIMIT",
                        "机器人未完成上传会话数量达到上限，请稍后重试",
                        true,
                        Map.of(
                                "scope", "ROBOT",
                                "robotId", robotId,
                                "activeCount", robotCount,
                                "limit", robotLimit));
            }
        }
    }

    private void markSimpleUploadFailed(MediaFile file, String code, String message) {
        file.setStatus(FileStatus.FAILED);
        file.setErrorCode(code);
        file.setErrorMessage(message);
        file.setUpdatedAt(now());
        fileRepository.save(file);
    }

    private MediaFileUpload requireActiveUpload(String robotId, String uploadId) {
        MediaFileUpload upload = requireUpload(robotId, uploadId);
        if (upload.getStatus() != FileUploadStatus.ACTIVE || upload.getExpiresAt().isBefore(now())) {
            throw error(HttpStatus.CONFLICT, "UPLOAD_NOT_ACTIVE", "上传会话已不再有效");
        }
        return upload;
    }

    private MediaFileUpload requireUpload(String robotId, String uploadId) {
        MediaFileUpload upload = uploadRepository.findById(uploadId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "UPLOAD_NOT_FOUND", "未找到上传任务"));
        MediaFile file = requireFile(upload.getFileId());
        if (robotId != null && !robotId.isBlank() && !Objects.equals(file.getRobotId(), robotId)) {
            throw error(HttpStatus.NOT_FOUND, "UPLOAD_NOT_FOUND", "未找到上传任务");
        }
        return upload;
    }

    private long calculatePartSize(long fileSize) {
        long mebibyte = 1024L * 1024L;
        long minimumPartSize = 5L * mebibyte;
        long maximumPartSize = 5L * 1024L * 1024L * 1024L;
        int maximumPartCount = Math.max(1, properties.getFile().getMaxPartCount());
        long required = ceilDiv(fileSize, maximumPartCount);
        long configured = Math.max(properties.getFile().getPartSizeBytes(), minimumPartSize);
        long selected = Math.max(configured, required);
        long rounded = ceilDiv(selected, mebibyte) * mebibyte;
        if (rounded > maximumPartSize || ceilDiv(fileSize, rounded) > maximumPartCount) {
            throw error(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "文件超过分片上传能力上限");
        }
        return rounded;
    }

    private long ceilDiv(long dividend, long divisor) {
        if (dividend <= 0) {
            return 0;
        }
        return 1L + (dividend - 1L) / divisor;
    }

    private MediaFile requireFile(String fileId) {
        return fileRepository.findById(fileId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "FILE_NOT_FOUND", "未找到文件"));
    }

    private MediaFile requirePlayableFile(CurrentUser user, String fileId) {
        MediaFile file = requireFile(fileId);
        requireFileAccess(user, file, MEDIA_VIEWER, "FILE_NOT_READY", "文件尚未就绪");
        if (file.getStatus() != FileStatus.READY) {
            throw error(HttpStatus.NOT_FOUND, "FILE_NOT_READY", "文件尚未就绪");
        }
        return file;
    }

    private Optional<MediaFile> findActiveLiveRecording(String sessionId, CurrentUser user) {
        return fileRepository
                .findFirstByFileTypeAndStatusAndSourceFileIdStartingWithOrderByUpdatedAtAsc(
                        FileType.VIDEO,
                        FileStatus.UPLOADING,
                        "livekit-egress:" + sessionId + ":")
                .filter(file -> user == null
                        || (Objects.equals(file.getOrgId(), user.orgId())
                                && Objects.equals(file.getCreatedBy(), user.userId())));
    }

    private void requireFileAccess(
            CurrentUser user,
            MediaFile file,
            String role,
            String notFoundCode,
            String notFoundMessage) {
        if (user == null
                || !Objects.equals(file.getOrgId(), user.orgId())
                || (file.getCreatedBy() != null && !Objects.equals(file.getCreatedBy(), user.userId()))) {
            throw error(HttpStatus.NOT_FOUND, notFoundCode, notFoundMessage);
        }
        if (file.getCreatedBy() != null) {
            requireRole(user, role, MEDIA_OPERATOR.equals(role) ? "无手动媒体操作权限" : "无手动媒体查看权限");
        }
    }

    private boolean canViewUserOwnedFiles(CurrentUser user) {
        return user != null && (user.hasRole(MEDIA_VIEWER) || user.hasRole(MEDIA_OPERATOR));
    }

    private void requireRole(CurrentUser user, String role, String message) {
        if (user == null || (!user.hasRole(role) && !(MEDIA_VIEWER.equals(role) && user.hasRole(MEDIA_OPERATOR)))) {
            throw error(HttpStatus.FORBIDDEN, "MEDIA_PERMISSION_DENIED", message);
        }
    }

    private boolean isUserOwnedSource(String metadata) {
        if (metadata == null || metadata.isBlank()) {
            return false;
        }
        try {
            Map<String, Object> values = objectMapper.readValue(metadata, new TypeReference<>() {});
            return USER_OWNED_SOURCES.contains(String.valueOf(values.get("source")));
        } catch (Exception ex) {
            return false;
        }
    }

    private void refresh(MediaFileUpload upload) {
        upload.setExpiresAt(now().plusHours(properties.getFile().getMultipartExpireHours()));
        upload.setLastActiveAt(now());
        uploadRepository.save(upload);
    }

    private MediaVideoFile ensureVideo(MediaFile file, VideoFileStatus status) {
        MediaVideoFile video = videoRepository.findById(file.getFileId()).orElseGet(() -> newVideo(file, status));
        video.setStatus(status);
        video.setProcessingStartedAt(video.getProcessingStartedAt() == null ? now() : video.getProcessingStartedAt());
        return videoRepository.save(video);
    }

    private MediaVideoFile newVideo(MediaFile file, VideoFileStatus status) {
        MediaVideoFile video = new MediaVideoFile();
        video.setFileId(file.getFileId());
        video.setStatus(status);
        video.setProcessingStartedAt(now());
        return video;
    }

    private FileStatusResponse status(MediaFile file) {
        return new FileStatusResponse(
                file.getFileId(),
                file.getStatus().name(),
                file.getFileSize(),
                file.getStatus() == FileStatus.READY,
                file.getErrorCode(),
                file.getErrorMessage(),
                file.getUploadedAt());
    }

    private FileListItemResponse item(MediaFile file) {
        MediaVideoFile video = file.getFileType() == FileType.VIDEO
                ? videoRepository.findById(file.getFileId()).orElse(null)
                : null;
        return new FileListItemResponse(
                file.getFileId(),
                file.getRobotId(),
                file.getDeviceId(),
                file.getExtensionId(),
                file.getFileType().name(),
                file.getFileName(),
                file.getContentType(),
                file.getFileSize(),
                video == null ? null : video.getDurationSeconds(),
                video == null ? null : video.getStartedAt(),
                video == null ? null : video.getEndedAt(),
                video == null ? null : video.getWidth(),
                video == null ? null : video.getHeight(),
                file.getStatus().name(),
                video == null ? null : video.getStatus().name(),
                file.getErrorCode(),
                file.getUploadedAt(),
                file.getCreatedAt(),
                file.getMetadataJson(),
                elapsedSeconds(video));
    }

    private Integer elapsedSeconds(MediaVideoFile video) {
        if (video == null || video.getStartedAt() == null || video.getStatus() != VideoFileStatus.PROCESSING) {
            return null;
        }
        return Math.max(0, (int) Duration.between(video.getStartedAt(), OffsetDateTime.now(ZoneOffset.UTC)).toSeconds());
    }

    private void alignVideoTimeRange(MediaFile file, MediaVideoFile video) {
        Integer durationSeconds = video.getDurationSeconds();
        if (durationSeconds == null || durationSeconds <= 0) {
            return;
        }
        OffsetDateTime startAt = video.getStartedAt();
        OffsetDateTime endAt = video.getEndedAt();
        if (startAt == null) {
            endAt = firstNonNull(sourceFileEndAt(file.getSourceFileId()), endAt, file.getUploadedAt(), file.getCreatedAt());
            startAt = endAt == null ? null : endAt.minusSeconds(durationSeconds);
        } else {
            endAt = startAt.plusSeconds(durationSeconds);
        }
        video.setStartedAt(startAt);
        video.setEndedAt(endAt);
    }

    private OffsetDateTime firstNonNull(OffsetDateTime... values) {
        for (OffsetDateTime value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private OffsetDateTime sourceFileEndAt(String sourceFileId) {
        if (sourceFileId == null || sourceFileId.isBlank()) {
            return null;
        }
        String[] parts = sourceFileId.split("/");
        if (parts.length < 2) {
            return null;
        }
        try {
            long epochSeconds = Long.parseLong(parts[parts.length - 1]);
            return OffsetDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC);
        } catch (Exception ex) {
            return null;
        }
    }

    private String playlistAssetName(MediaFile file, MediaVideoFile video) {
        String playlistKey = video.getHlsPlaylistObjectKey();
        String prefix = storage.hlsPrefix(file.getObjectKey());
        if (playlistKey != null && playlistKey.startsWith(prefix)) {
            return playlistKey.substring(prefix.length());
        }
        return "index.m3u8";
    }

    private void requireProxySize(String objectKey) {
        if (storage.statSize(objectKey) > MAX_PROXIED_FILE_BYTES) {
            throw error(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "FILE_CONTENT_TOO_LARGE",
                    "文件正文超过代理读取上限，请使用下载地址");
        }
    }

    private String rewriteHlsLine(String line, String encodedToken) {
        if (line.startsWith("#EXT-X-MAP:")) {
            return rewriteQuotedUri(line, encodedToken);
        }
        if (line.isBlank() || line.startsWith("#") || line.contains("://")) {
            return line;
        }
        return appendToken(line, encodedToken);
    }

    private String rewriteQuotedUri(String line, String encodedToken) {
        String marker = "URI=\"";
        int start = line.indexOf(marker);
        if (start < 0) {
            return line;
        }
        int uriStart = start + marker.length();
        int uriEnd = line.indexOf('"', uriStart);
        if (uriEnd < 0) {
            return line;
        }
        String uri = line.substring(uriStart, uriEnd);
        if (uri.isBlank() || uri.contains("://")) {
            return line;
        }
        return line.substring(0, uriStart) + appendToken(uri, encodedToken) + line.substring(uriEnd);
    }

    private String appendToken(String uri, String encodedToken) {
        return uri + (uri.contains("?") ? "&" : "?") + "token=" + encodedToken;
    }

    private String signPlayback(String fileId, long expiresAt) {
        String payload = fileId + "." + expiresAt;
        return payload + "." + signature(payload);
    }

    private void verifyPlayback(String fileId, String token) {
        if (token == null || token.isBlank()) {
            throw error(HttpStatus.UNAUTHORIZED, "PLAY_TOKEN_MISSING", "播放 token 缺失");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3 || !Objects.equals(parts[0], fileId)) {
            throw error(HttpStatus.UNAUTHORIZED, "PLAY_TOKEN_INVALID", "播放 token 无效");
        }
        long expiresAt;
        try {
            expiresAt = Long.parseLong(parts[1]);
        } catch (NumberFormatException ex) {
            throw error(HttpStatus.UNAUTHORIZED, "PLAY_TOKEN_INVALID", "播放 token 无效");
        }
        if (expiresAt < now().toEpochSecond()) {
            throw error(HttpStatus.UNAUTHORIZED, "PLAY_TOKEN_EXPIRED", "播放 token 已过期");
        }
        String payload = parts[0] + "." + parts[1];
        if (!Objects.equals(signature(payload), parts[2])) {
            throw error(HttpStatus.UNAUTHORIZED, "PLAY_TOKEN_INVALID", "播放 token 无效");
        }
    }

    private String signature(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getFile().getPlayTokenSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("生成播放签名失败", ex);
        }
    }

    private String hlsContentType(String objectName) {
        if (objectName.endsWith(".m3u8")) {
            return "application/vnd.apple.mpegurl";
        }
        if (objectName.endsWith(".m4s") || objectName.endsWith(".mp4")) {
            return "video/mp4";
        }
        if (objectName.endsWith(".ts")) {
            return "video/mp2t";
        }
        return "application/octet-stream";
    }

    private String videoSourceName(FileType fileType, String fileName) {
        return fileType == FileType.VIDEO ? "source.mp4" : fileName;
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return values.stream()
                .map(this::stringValue)
                .filter(Objects::nonNull)
                .toList();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private String id() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private FileApiException error(HttpStatus status, String code, String message) {
        return new FileApiException(status, code, message);
    }

    private String truncate(String message) {
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    private String writeMetadata(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private String liveMetadata(MediaFile file, String key) {
        try {
            Map<String, Object> map = metadataMap(file);
            Object value = map.get(key);
            return value == null ? "" : String.valueOf(value);
        } catch (Exception ex) {
            return "";
        }
    }

    private Map<String, Object> metadataMap(MediaFile file) {
        try {
            if (file.getMetadataJson() == null || file.getMetadataJson().isBlank()) {
                return new LinkedHashMap<>();
            }
            return objectMapper.readValue(file.getMetadataJson(), new TypeReference<>() {});
        } catch (Exception ex) {
            return new LinkedHashMap<>();
        }
    }

    /**
     * 已读取的有限大小正文及实际媒体类型，供文件和 HLS 代理返回。
     *
     * @param bytes 内容的原始二进制字节
     * @param contentType 文件媒体类型
     */
    public record PlaybackAsset(byte[] bytes, String contentType) {
    }

    /**
     * 手动录像或视频处理回写的尺寸、时间、时长及编码等媒体元数据。
     *
     * @param videoCodec 视频编码名称
     * @param audioCodec 音频编码名称，未探测到音轨时可为空
     * @param width 宽度
     * @param height 高度
     * @param durationSeconds 时长秒数
     */
    public record VideoProbeResult(
            String videoCodec,
            String audioCodec,
            Integer width,
            Integer height,
            double durationSeconds) {
    }
}
