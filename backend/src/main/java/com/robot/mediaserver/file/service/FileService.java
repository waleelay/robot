package com.robot.mediaserver.file.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.mediaserver.auth.CurrentUser;
import com.robot.mediaserver.config.MediaProperties;
import com.robot.mediaserver.file.dto.CreateMultipartFileUploadRequest;
import com.robot.mediaserver.file.dto.FileDownloadUrlResponse;
import com.robot.mediaserver.file.dto.FileListItemResponse;
import com.robot.mediaserver.file.dto.FileListResponse;
import com.robot.mediaserver.file.dto.FilePartInfoResponse;
import com.robot.mediaserver.file.dto.FilePartUploadUrlResponse;
import com.robot.mediaserver.file.dto.FilePartUrlsResponse;
import com.robot.mediaserver.file.dto.FilePlayUrlResponse;
import com.robot.mediaserver.file.dto.FileStatusResponse;
import com.robot.mediaserver.file.dto.FileUploadResponse;
import com.robot.mediaserver.file.model.FileStatus;
import com.robot.mediaserver.file.model.FileType;
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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FileService {

    private final MediaProperties properties;
    private final MediaFileRepository fileRepository;
    private final MediaFileUploadRepository uploadRepository;
    private final MediaVideoFileRepository videoRepository;
    private final FileObjectStorageService storage;
    private final LiveKitEgressService egressService;
    private final ObjectMapper objectMapper;

    public FileService(
            MediaProperties properties,
            MediaFileRepository fileRepository,
            MediaFileUploadRepository uploadRepository,
            MediaVideoFileRepository videoRepository,
            FileObjectStorageService storage,
            LiveKitEgressService egressService,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.fileRepository = fileRepository;
        this.uploadRepository = uploadRepository;
        this.videoRepository = videoRepository;
        this.storage = storage;
        this.egressService = egressService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public FileListItemResponse uploadSimple(
            CurrentUser user,
            MultipartFile file,
            FileType fileType,
            String robotId,
            String deviceId,
            String taskExecutionId,
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
                taskExecutionId,
                sourceFileId,
                fileType,
                file.getOriginalFilename() == null ? "file" : file.getOriginalFilename(),
                file.getContentType() == null ? "application/octet-stream" : file.getContentType(),
                file.getSize(),
                FileUploadMode.SIMPLE,
                metadata,
                timestamp);
        fileRepository.save(entity);
        try {
            storage.upload(entity.getObjectKey(), file.getInputStream(), file.getSize(), entity.getContentType());
        } catch (Exception ex) {
            entity.setStatus(FileStatus.FAILED);
            entity.setErrorCode("SIMPLE_UPLOAD_FAILED");
            entity.setErrorMessage(ex.getMessage());
            entity.setUpdatedAt(now());
            fileRepository.save(entity);
            throw error(HttpStatus.CONFLICT, "SIMPLE_UPLOAD_FAILED", ex.getMessage());
        }
        markUploaded(entity);
        return item(entity);
    }

    @Transactional
    public FileUploadResponse createOrResumeMultipart(String robotIdHeader, CreateMultipartFileUploadRequest request) {
        String robotId = firstNonBlank(request.getRobotId(), robotIdHeader);
        if (request.getFileSize() > properties.getFile().getMaxFileSizeBytes()) {
            throw error(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "文件超过配置的大小限制");
        }
        MediaFile file = null;
        if (robotId != null && request.getSourceFileId() != null && !request.getSourceFileId().isBlank()) {
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
                    request.getTaskExecutionId(),
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

    @Transactional
    public FilePartUrlsResponse partUrls(String robotId, String uploadId, List<Integer> partNumbers) {
        MediaFileUpload upload = requireActiveUpload(robotId, uploadId);
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

    @Transactional
    public FileStatusResponse completeMultipart(String robotId, String uploadId) {
        MediaFileUpload upload = requireUpload(robotId, uploadId);
        MediaFile file = requireFile(upload.getFileId());
        if (upload.getStatus() == FileUploadStatus.COMPLETED) {
            return status(file);
        }
        List<FileObjectStorageService.StoredPart> parts = storage.listParts(file.getObjectKey(), upload.getStorageUploadId());
        validateUploadedParts(file, upload, parts);
        storage.completeMultipart(file.getObjectKey(), upload.getStorageUploadId(), parts);
        long storedSize = storage.statSize(file.getObjectKey());
        if (storedSize != file.getFileSize()) {
            throw error(HttpStatus.CONFLICT, "FILE_SIZE_MISMATCH", "合成后的对象大小与登记文件不一致");
        }
        upload.setStatus(FileUploadStatus.COMPLETED);
        upload.setCompletedAt(now());
        upload.setLastActiveAt(now());
        uploadRepository.save(upload);
        markUploaded(file);
        return status(file);
    }

    public FileStatusResponse fileStatus(String robotId, String fileId) {
        MediaFile file = requireFile(fileId);
        if (robotId != null && !robotId.isBlank() && !Objects.equals(file.getRobotId(), robotId)) {
            throw error(HttpStatus.NOT_FOUND, "FILE_NOT_FOUND", "未找到文件");
        }
        return status(file);
    }

    public FileListResponse list(
            CurrentUser user,
            String robotId,
            String deviceId,
            String taskExecutionId,
            FileType fileType,
            FileStatus status,
            int page,
            int size) {
        Specification<MediaFile> spec = (root, query, cb) -> cb.equal(root.get("orgId"), user.orgId());
        if (robotId != null && !robotId.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("robotId"), robotId));
        }
        if (deviceId != null && !deviceId.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("deviceId"), deviceId));
        }
        if (taskExecutionId != null && !taskExecutionId.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("taskExecutionId"), taskExecutionId));
        }
        if (fileType != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("fileType"), fileType));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        Page<MediaFile> result = fileRepository.findAll(
                spec,
                PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)), Sort.by(Sort.Direction.DESC, "createdAt")));
        return new FileListResponse(result.stream().map(this::item).toList(), result.getNumber(), result.getSize(), result.getTotalElements());
    }

    public FileListItemResponse detail(CurrentUser user, String fileId) {
        MediaFile file = requireFile(fileId);
        if (!Objects.equals(file.getOrgId(), user.orgId())) {
            throw error(HttpStatus.NOT_FOUND, "FILE_NOT_FOUND", "未找到文件");
        }
        return item(file);
    }

    public FileDownloadUrlResponse downloadUrl(CurrentUser user, String fileId) {
        MediaFile file = requirePlayableFile(user, fileId);
        OffsetDateTime expiresAt = now().plusSeconds(properties.getFile().getPlayUrlTtlSeconds());
        return new FileDownloadUrlResponse(
                fileId,
                storage.presignDownload(file.getObjectKey(), properties.getFile().getPlayUrlTtlSeconds()),
                expiresAt);
    }

    public PlaybackAsset content(CurrentUser user, String fileId) {
        MediaFile file = requirePlayableFile(user, fileId);
        return new PlaybackAsset(storage.readObject(file.getObjectKey()), file.getContentType());
    }

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
        String path = "/api/control/files/" + fileId + "/hls/" + playlistAssetName(file, video) + "?token="
                + URLEncoder.encode(token, StandardCharsets.UTF_8);
        return new FilePlayUrlResponse(fileId, "hls", "application/vnd.apple.mpegurl", path, expiresAt);
    }

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

    @Transactional
    public FileListItemResponse startLiveRecording(VideoSession session, CurrentUser user) {
        MediaFile active = findActiveLiveRecording(session.getSessionId(), user).orElse(null);
        if (active != null) {
            throw error(HttpStatus.CONFLICT, "RECORDING_ALREADY_ACTIVE", "当前视频正在录制中");
        }
        OffsetDateTime timestamp = now();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", "LIVEKIT_EGRESS");
        metadata.put("sessionId", session.getSessionId());
        metadata.put("roomName", session.getRoomName());
        metadata.put("trackSid", session.getTrackSid());
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
        file.setStatus(FileStatus.UPLOADING);
        fileRepository.save(file);
        ensureVideo(file, VideoFileStatus.PROCESSING);
        try {
            LiveKitEgressService.EgressStartResult result = egressService.startRoomMp4(session.getRoomName(), file.getObjectKey());
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

    @Transactional
    public FileListItemResponse stopLiveRecording(String sessionId, String fileId, CurrentUser user) {
        MediaFile file = requireFile(fileId);
        if (!Objects.equals(file.getOrgId(), user.orgId()) || !liveMetadata(file, "sessionId").equals(sessionId)) {
            throw error(HttpStatus.NOT_FOUND, "RECORDING_NOT_ACTIVE", "录像未在进行中");
        }
        String startedClientId = liveMetadata(file, "startedClientId");
        if (!startedClientId.isBlank() && !Objects.equals(startedClientId, user.clientId())) {
            throw error(HttpStatus.CONFLICT, "RECORDING_STARTED_BY_OTHER_CLIENT", "当前录像由其他浏览器发起");
        }
        finishLiveRecording(file);
        return item(file);
    }

    @Transactional
    public boolean stopLiveRecordingForClient(String sessionId, String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return false;
        }
        MediaFile active = findActiveLiveRecording(sessionId, null)
                .filter(file -> Objects.equals(liveMetadata(file, "startedClientId"), clientId))
                .orElse(null);
        if (active == null) {
            return false;
        }
        finishLiveRecording(active);
        return true;
    }

    public FileListItemResponse activeLiveRecording(String sessionId, CurrentUser user) {
        return findActiveLiveRecording(sessionId, user).map(this::item).orElse(null);
    }

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
        video.setDurationSeconds((int) Math.ceil(probe.durationSeconds()));
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
                file.setStatus(FileStatus.FAILED);
                file.setErrorCode("EGRESS_STOP_FAILED");
                file.setErrorMessage(ex.getMessage());
                file.setUpdatedAt(now());
                fileRepository.save(file);
                throw error(HttpStatus.CONFLICT, "EGRESS_STOP_FAILED", ex.getMessage());
            }
        }
        try {
            long sourceSize = storage.statSize(file.getObjectKey());
            file.setFileSize(sourceSize);
        } catch (Exception ignored) {
            // LiveKit may expose the MP4 shortly after stop returns; HLS worker will retry later.
        }
        file.setUploadedAt(now());
        file.setStatus(FileStatus.PROCESSING);
        file.setUpdatedAt(now());
        ensureVideo(file, VideoFileStatus.PROCESSING);
        fileRepository.save(file);
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

    private MediaFile newFile(
            String orgId,
            String robotId,
            String deviceId,
            String taskExecutionId,
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
        file.setTaskExecutionId(blankToNull(taskExecutionId));
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
        upload.setPartSize(properties.getFile().getPartSizeBytes());
        upload.setPartCount((int) Math.ceil((double) file.getFileSize() / upload.getPartSize()));
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

    private void validateUploadedParts(MediaFile file, MediaFileUpload upload, List<FileObjectStorageService.StoredPart> parts) {
        if (parts.size() != upload.getPartCount()) {
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
        if (total != file.getFileSize()) {
            throw error(HttpStatus.CONFLICT, "UPLOAD_SIZE_MISMATCH", "已上传分片大小与登记文件不一致");
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
        if (uploadRepository.countByStatus(FileUploadStatus.ACTIVE) >= properties.getFile().getMaxActiveUploadsGlobal()
                || (robotId != null && uploadRepository.countActiveByRobotId(robotId, FileUploadStatus.ACTIVE)
                        >= properties.getFile().getMaxActiveUploadsPerRobot())) {
            throw error(HttpStatus.TOO_MANY_REQUESTS, "UPLOAD_CONCURRENCY_LIMIT", "上传并发数超过限制");
        }
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

    private MediaFile requireFile(String fileId) {
        return fileRepository.findById(fileId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "FILE_NOT_FOUND", "未找到文件"));
    }

    private MediaFile requirePlayableFile(CurrentUser user, String fileId) {
        MediaFile file = requireFile(fileId);
        if (!Objects.equals(file.getOrgId(), user.orgId()) || file.getStatus() != FileStatus.READY) {
            throw error(HttpStatus.NOT_FOUND, "FILE_NOT_READY", "文件尚未就绪");
        }
        return file;
    }

    private Optional<MediaFile> findActiveLiveRecording(String sessionId, CurrentUser user) {
        List<MediaFile> candidates = fileRepository.findTop10ByFileTypeAndStatusOrderByUpdatedAtAsc(FileType.VIDEO, FileStatus.UPLOADING);
        return candidates.stream()
                .filter(file -> file.getSourceFileId() != null && file.getSourceFileId().startsWith("livekit-egress:" + sessionId + ":"))
                .filter(file -> user == null || Objects.equals(file.getOrgId(), user.orgId()))
                .findFirst();
    }

    private void refresh(MediaFileUpload upload) {
        upload.setExpiresAt(now().plusHours(properties.getFile().getMultipartExpireHours()));
        upload.setLastActiveAt(now());
        uploadRepository.save(upload);
    }

    private void ensureVideo(MediaFile file, VideoFileStatus status) {
        MediaVideoFile video = videoRepository.findById(file.getFileId()).orElseGet(() -> newVideo(file, status));
        video.setStatus(status);
        video.setProcessingStartedAt(video.getProcessingStartedAt() == null ? now() : video.getProcessingStartedAt());
        videoRepository.save(video);
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
                file.getTaskExecutionId(),
                file.getFileType().name(),
                file.getFileName(),
                file.getContentType(),
                file.getFileSize(),
                video == null ? null : video.getDurationSeconds(),
                video == null ? null : video.getWidth(),
                video == null ? null : video.getHeight(),
                file.getStatus().name(),
                video == null ? null : video.getStatus().name(),
                file.getErrorCode(),
                file.getUploadedAt(),
                file.getCreatedAt(),
                file.getMetadataJson());
    }

    private String playlistAssetName(MediaFile file, MediaVideoFile video) {
        String playlistKey = video.getHlsPlaylistObjectKey();
        String prefix = storage.hlsPrefix(file.getObjectKey());
        if (playlistKey != null && playlistKey.startsWith(prefix)) {
            return playlistKey.substring(prefix.length());
        }
        return "index.m3u8";
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

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : blankToNull(second);
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

    private ResponseStatusException error(HttpStatus status, String code, String message) {
        return new ResponseStatusException(status, message == null ? code : message);
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
            Map<String, Object> map = objectMapper.readValue(file.getMetadataJson(), new TypeReference<>() {});
            Object value = map.get(key);
            return value == null ? "" : String.valueOf(value);
        } catch (Exception ex) {
            return "";
        }
    }

    public record PlaybackAsset(byte[] bytes, String contentType) {
    }

    public record VideoProbeResult(
            String videoCodec,
            String audioCodec,
            Integer width,
            Integer height,
            double durationSeconds) {
    }
}
