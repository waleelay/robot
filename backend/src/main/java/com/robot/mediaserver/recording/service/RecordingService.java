package com.robot.mediaserver.recording.service;

import com.robot.mediaserver.auth.CurrentUser;
import com.robot.mediaserver.config.MediaProperties;
import com.robot.mediaserver.recording.api.RecordingApiException;
import com.robot.mediaserver.recording.dto.CreateRecordingUploadRequest;
import com.robot.mediaserver.recording.dto.PartInfoResponse;
import com.robot.mediaserver.recording.dto.PartUploadUrlResponse;
import com.robot.mediaserver.recording.dto.PartUrlsResponse;
import com.robot.mediaserver.recording.dto.PlaybackUrlResponse;
import com.robot.mediaserver.recording.dto.RecordingListItemResponse;
import com.robot.mediaserver.recording.dto.RecordingListResponse;
import com.robot.mediaserver.recording.dto.RecordingStatusResponse;
import com.robot.mediaserver.recording.dto.RecordingUploadResponse;
import com.robot.mediaserver.recording.model.MediaRecording;
import com.robot.mediaserver.recording.model.MediaRecordingUpload;
import com.robot.mediaserver.recording.model.RecordingStatus;
import com.robot.mediaserver.recording.model.UploadStatus;
import com.robot.mediaserver.recording.repository.MediaRecordingRepository;
import com.robot.mediaserver.recording.repository.MediaRecordingUploadRepository;
import com.robot.mediaserver.storage.RecordingObjectStorageService;
import com.robot.mediaserver.storage.RecordingObjectStorageService.StoredPart;
import jakarta.transaction.Transactional;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 录像上传、查询和回放服务。
 *
 * <p>负责机器人侧录像断点续传、控制端录像检索、HLS 回放签名、
 * 预览缩略图资源代理，以及录像对象存储生命周期处理。</p>
 *
 * @author leelay
 * @date 2026/05/31
 */
@Service
public class RecordingService {

    /**
     * 媒体服务配置属性。
     */
    private final MediaProperties properties;

    /**
     * 录像元数据仓储。
     */
    private final MediaRecordingRepository recordingRepository;

    /**
     * 录像上传会话仓储。
     */
    private final MediaRecordingUploadRepository uploadRepository;

    /**
     * 录像对象存储服务。
     */
    private final RecordingObjectStorageService storage;

    /**
     * 构造录像服务。
     *
     * @param properties 媒体服务配置属性
     * @param recordingRepository 录像元数据仓储
     * @param uploadRepository 录像上传会话仓储
     * @param storage 录像对象存储服务
     */
    public RecordingService(
            MediaProperties properties,
            MediaRecordingRepository recordingRepository,
            MediaRecordingUploadRepository uploadRepository,
            RecordingObjectStorageService storage) {
        this.properties = properties;
        this.recordingRepository = recordingRepository;
        this.uploadRepository = uploadRepository;
        this.storage = storage;
    }

    /**
     * 创建或恢复机器人侧录像上传任务。
     *
     * <p>机器人客户端会周期扫描本地 mp4，并用 sourceFileId 做幂等键。
     * 如果同一个文件已经上传完成或正在转码，直接返回 completedResponse；
     * 如果仍有 ACTIVE multipart upload，则刷新过期时间并让客户端续传缺失分片。</p>
     *
     * @param robotId 机器人编号
     * @param request 创建或恢复上传请求
     * @return 上传任务响应
     */
    @Transactional
    public RecordingUploadResponse createOrResume(String robotId, CreateRecordingUploadRequest request) {
        requireRobotId(robotId);
        validateSource(request);
        MediaRecording recording = recordingRepository.findByRobotIdAndSourceFileId(robotId, request.getSourceFileId())
                .orElse(null);
        if (recording != null) {
            ensureSameSource(recording, request);
            if (recording.getStatus() == RecordingStatus.READY
                    || recording.getStatus() == RecordingStatus.VERIFYING
                    || recording.getStatus() == RecordingStatus.PROCESSING_PLAYBACK) {
                return completedResponse(recording);
            }
            MediaRecordingUpload active = uploadRepository
                    .findFirstByRecordingIdAndStatusOrderByCreatedAtDesc(recording.getRecordingId(), UploadStatus.ACTIVE)
                    .orElse(null);
            if (active != null && active.getExpiresAt().isAfter(now())) {
                refresh(active);
                return uploadResponse(recording, active, true);
            }
        } else {
            recording = newRecording(robotId, request);
            recordingRepository.save(recording);
        }
        checkQuota(robotId);
        MediaRecordingUpload upload = newUpload(recording);
        recording.setStatus(RecordingStatus.UPLOADING);
        recording.setUpdatedAt(now());
        recordingRepository.save(recording);
        uploadRepository.save(upload);
        return uploadResponse(recording, upload, true);
    }

    /**
     * 给客户端签发一小批分片上传 URL。
     *
     * <p>一次最多返回配置上限内的一批 URL，用于降低大文件上传时的控制面请求次数；
     * 客户端需要边传边取，服务端据此刷新上传会话活跃时间。</p>
     *
     * @param robotId 机器人编号
     * @param uploadId 上传会话编号
     * @param partNumbers 需要签名的分片序号集合
     * @return 分片上传地址响应
     */
    @Transactional
    public PartUrlsResponse partUrls(String robotId, String uploadId, List<Integer> partNumbers) {
        MediaRecordingUpload upload = requireActiveUpload(robotId, uploadId);
        MediaRecording recording = requireRecording(upload.getRecordingId());
        int maxPartUrls = Math.max(1, properties.getRecording().getMaxPartUrlsPerRequest());
        if (partNumbers.size() > maxPartUrls) {
            throw error(HttpStatus.BAD_REQUEST, "TOO_MANY_PART_URLS", "Too many part URLs requested at once");
        }
        for (Integer part : partNumbers) {
            if (part == null || part < 1 || part > upload.getPartCount()) {
                throw error(HttpStatus.BAD_REQUEST, "INVALID_PART_NUMBER", "Invalid part number");
            }
        }
        refresh(upload);
        List<PartUploadUrlResponse> urls = partNumbers.stream()
                .distinct()
                .map(number -> new PartUploadUrlResponse(
                        number,
                        storage.presignUploadPart(recording.getSourceObjectKey(), upload.getStorageUploadId(), number)))
                .toList();
        return new PartUrlsResponse(now().plusSeconds(properties.getRecording().getUploadUrlTtlSeconds()), urls);
    }

    /**
     * 完成 multipart upload，并把录像状态推进到 VERIFYING。
     *
     * <p>这里会检查分片数量、序号连续性和总大小，
     * 避免客户端漏传或源文件变化后仍被合并。</p>
     *
     * @param robotId 机器人编号
     * @param uploadId 上传会话编号
     * @return 录像状态响应
     */
    @Transactional
    public RecordingStatusResponse complete(String robotId, String uploadId) {
        MediaRecordingUpload upload = requireUpload(robotId, uploadId);
        MediaRecording recording = requireRecording(upload.getRecordingId());
        if (upload.getStatus() == UploadStatus.COMPLETED) {
            return status(recording);
        }
        List<StoredPart> parts = storage.listParts(recording.getSourceObjectKey(), upload.getStorageUploadId());
        validateUploadedParts(recording, upload, parts);
        storage.completeMultipart(recording.getSourceObjectKey(), upload.getStorageUploadId(), parts);
        long storedSize = storage.statSize(recording.getSourceObjectKey());
        if (storedSize != recording.getFileSize()) {
            throw error(HttpStatus.CONFLICT, "SOURCE_SIZE_MISMATCH", "Completed object size does not match registered file");
        }
        OffsetDateTime timestamp = now();
        upload.setStatus(UploadStatus.COMPLETED);
        upload.setCompletedAt(timestamp);
        upload.setLastActiveAt(timestamp);
        recording.setStatus(RecordingStatus.VERIFYING);
        recording.setUploadedAt(timestamp);
        recording.setUpdatedAt(timestamp);
        uploadRepository.save(upload);
        recordingRepository.save(recording);
        return status(recording);
    }

    /**
     * 查询机器人侧可见的录像状态。
     *
     * @param robotId 机器人编号
     * @param recordingId 录像编号
     * @return 录像状态响应
     */
    public RecordingStatusResponse robotStatus(String robotId, String recordingId) {
        MediaRecording recording = requireRobotRecording(robotId, recordingId);
        return status(recording);
    }

    /**
     * 查询控制端录像列表。
     *
     * @param user 当前操作用户
     * @param robotId 机器人编号，可为空
     * @param deviceId 摄像头设备编号，可为空
     * @param status 录像状态，可为空
     * @param from 录像开始时间下界，可为空
     * @param to 录像开始时间上界，可为空
     * @param page 页码
     * @param size 每页条数
     * @return 录像列表响应
     */
    public RecordingListResponse list(
            CurrentUser user,
            String robotId,
            String deviceId,
            RecordingStatus status,
            OffsetDateTime from,
            OffsetDateTime to,
            int page,
            int size) {
        // 控制端查询始终限定在当前 orgId 内，其他过滤条件按需叠加。
        Specification<MediaRecording> spec = (root, query, cb) -> cb.equal(root.get("orgId"), user.orgId());
        if (robotId != null && !robotId.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("robotId"), robotId));
        }
        if (deviceId != null && !deviceId.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("deviceId"), deviceId));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("recordedStartedAt"), from));
        }
        if (to != null) {
            spec = spec.and((root, query, cb) -> cb.lessThan(root.get("recordedStartedAt"), to));
        }
        Page<MediaRecording> result = recordingRepository.findAll(
                spec,
                PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size))));
        return new RecordingListResponse(result.stream().map(this::item).toList(), result.getNumber(), result.getSize(), result.getTotalElements());
    }

    /**
     * 生成控制端 HLS 回放入口地址。
     *
     * @param user 当前操作用户
     * @param recordingId 录像编号
     * @return HLS 回放地址响应
     */
    public PlaybackUrlResponse playUrl(CurrentUser user, String recordingId) {
        MediaRecording recording = requireRecording(recordingId);
        if (!Objects.equals(recording.getOrgId(), user.orgId()) || recording.getStatus() != RecordingStatus.READY) {
            throw error(HttpStatus.NOT_FOUND, "RECORDING_NOT_PLAYABLE", "Recording is not playable");
        }
        OffsetDateTime expiresAt = now().plusSeconds(properties.getRecording().getPlayUrlTtlSeconds());
        String token = signPlayback(recordingId, expiresAt.toEpochSecond());
        // 这里返回的是后端代理路径，不直接暴露对象存储地址。
        // 后续每个 HLS 资源都会校验 token。
        String path = "/api/control/recordings/" + recordingId + "/hls/" + playlistAssetName(recording) + "?token="
                + URLEncoder.encode(token, StandardCharsets.UTF_8);
        return new PlaybackUrlResponse(recordingId, "hls", "application/vnd.apple.mpegurl", path, expiresAt);
    }

    public PlaybackAsset playbackAsset(String recordingId, String objectName, String token) {
        verifyPlayback(recordingId, token);
        if (!objectName.matches("[A-Za-z0-9_.-]+")) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_ASSET_NAME", "Invalid HLS asset name");
        }
        MediaRecording recording = requireRecording(recordingId);
        if (recording.getStatus() != RecordingStatus.READY) {
            throw error(HttpStatus.NOT_FOUND, "RECORDING_NOT_PLAYABLE", "Recording is not playable");
        }
        String objectKey = hlsPrefix(recording) + objectName;
        byte[] bytes = storage.readObject(objectKey);
        if (objectName.endsWith(".m3u8")) {
            // m3u8 中的相对分片路径也要补 token，否则播放器后续请求 ts 时会被拒绝。
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
     * 使上传会话过期，并回收未完成的对象存储 multipart upload。
     *
     * @param upload 上传会话实体
     */
    @Transactional
    public void expireUpload(MediaRecordingUpload upload) {
        if (upload.getStatus() != UploadStatus.ACTIVE) {
            return;
        }
        // 上传会话过期后必须 abort 对象存储 multipart，否则服务端会残留未完成分片。
        MediaRecording recording = requireRecording(upload.getRecordingId());
        storage.abortMultipart(recording.getSourceObjectKey(), upload.getStorageUploadId());
        upload.setStatus(UploadStatus.EXPIRED);
        recording.setStatus(RecordingStatus.FAILED);
        recording.setErrorCode("UPLOAD_EXPIRED");
        recording.setErrorMessage("Upload session expired");
        recording.setUpdatedAt(now());
        uploadRepository.save(upload);
        recordingRepository.save(recording);
    }

    /**
     * 删除录像相关的源文件、HLS 和预览资源。
     *
     * @param recording 录像实体
     */
    @Transactional
    public void deleteRecordingAssets(MediaRecording recording) {
        if (recording.getStatus() != RecordingStatus.READY && recording.getStatus() != RecordingStatus.DELETING) {
            return;
        }
        recording.setStatus(RecordingStatus.DELETING);
        recording.setUpdatedAt(now());
        recordingRepository.save(recording);
        // 当前对象布局以 recordingId 为目录隔离，删除 source/hls/preview 统一删父目录。
        String prefix = recording.getSourceObjectKey().substring(
                0,
                recording.getSourceObjectKey().indexOf("/original/source.mp4") + 1);
        storage.deletePrefix(prefix);
        recording.setStatus(RecordingStatus.DELETED);
        recording.setUpdatedAt(now());
        recordingRepository.save(recording);
    }

    private MediaRecording newRecording(String robotId, CreateRecordingUploadRequest request) {
        OffsetDateTime timestamp = now();
        OffsetDateTime recordedAt = request.getRecordedStartedAt() == null ? timestamp : request.getRecordedStartedAt();
        MediaRecording recording = new MediaRecording();
        recording.setRecordingId("rec_" + id());
        recording.setOrgId(properties.getRecording().getDefaultOrgId());
        recording.setRobotId(robotId);
        recording.setDeviceId(request.getDeviceId());
        recording.setSourceFileId(request.getSourceFileId());
        recording.setFileName(request.getFileName());
        recording.setSourceContentType(request.getContentType());
        recording.setFileSize(request.getFileSize());
        recording.setSha256(request.getSha256());
        recording.setRecordedStartedAt(recordedAt);
        recording.setReportedDurationSeconds(request.getDurationSeconds());
        recording.setSourceObjectKey(objectKey(recording, recordedAt));
        recording.setStatus(RecordingStatus.CREATED);
        recording.setCreatedAt(timestamp);
        recording.setUpdatedAt(timestamp);
        return recording;
    }

    private MediaRecordingUpload newUpload(MediaRecording recording) {
        OffsetDateTime timestamp = now();
        MediaRecordingUpload upload = new MediaRecordingUpload();
        upload.setUploadId("upl_" + id());
        upload.setRecordingId(recording.getRecordingId());
        upload.setStorageUploadId(storage.initiateMultipart());
        // partCount 按文件大小和配置分片大小计算，客户端必须上传 1..partCount 的连续分片。
        upload.setPartSize(properties.getRecording().getPartSizeBytes());
        upload.setPartCount((int) Math.ceil((double) recording.getFileSize() / upload.getPartSize()));
        upload.setStatus(UploadStatus.ACTIVE);
        upload.setCreatedAt(timestamp);
        upload.setLastActiveAt(timestamp);
        upload.setExpiresAt(timestamp.plusHours(properties.getRecording().getUploadExpireHours()));
        return upload;
    }

    private RecordingUploadResponse uploadResponse(MediaRecording recording, MediaRecordingUpload upload, boolean uploadRequired) {
        List<PartInfoResponse> parts = storage.listParts(recording.getSourceObjectKey(), upload.getStorageUploadId()).stream()
                .map(part -> new PartInfoResponse(part.partNumber(), part.etag(), part.size()))
                .toList();
        return new RecordingUploadResponse(
                recording.getRecordingId(),
                upload.getUploadId(),
                recording.getStatus().name(),
                uploadRequired,
                upload.getPartSize(),
                upload.getPartCount(),
                parts,
                upload.getExpiresAt());
    }

    private RecordingUploadResponse completedResponse(MediaRecording recording) {
        return new RecordingUploadResponse(
                recording.getRecordingId(),
                null,
                recording.getStatus().name(),
                false,
                properties.getRecording().getPartSizeBytes(),
                0,
                List.of(),
                null);
    }

    private void validateUploadedParts(MediaRecording recording, MediaRecordingUpload upload, List<StoredPart> parts) {
        if (parts.size() != upload.getPartCount()) {
            throw error(HttpStatus.CONFLICT, "UPLOAD_INCOMPLETE", "Not all parts have been uploaded");
        }
        // 对象存储返回的 part 列表按 partNumber 校验，防止乱序/缺号被误认为完成。
        long total = 0;
        for (int index = 0; index < parts.size(); index++) {
            StoredPart part = parts.get(index);
            if (part.partNumber() != index + 1) {
                throw error(HttpStatus.CONFLICT, "UPLOAD_INCOMPLETE", "Uploaded parts are not contiguous");
            }
            total += part.size();
        }
        if (total != recording.getFileSize()) {
            throw error(HttpStatus.CONFLICT, "UPLOAD_SIZE_MISMATCH", "Uploaded part sizes do not match registered file");
        }
    }

    private void validateSource(CreateRecordingUploadRequest request) {
        if (!"video/mp4".equalsIgnoreCase(request.getContentType())) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_VIDEO_FILE", "Only video/mp4 is accepted");
        }
        if (request.getFileSize() > properties.getRecording().getMaxFileSizeBytes()) {
            throw error(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "Recording exceeds configured size limit");
        }
    }

    private void ensureSameSource(MediaRecording recording, CreateRecordingUploadRequest request) {
        if (recording.getFileSize() != request.getFileSize()
                || !Objects.equals(recording.getSha256(), request.getSha256())) {
            throw error(HttpStatus.CONFLICT, "SOURCE_FILE_CHANGED", "Source file changed since upload was created");
        }
    }

    private void checkQuota(String robotId) {
        if (uploadRepository.countByStatus(UploadStatus.ACTIVE) >= properties.getRecording().getMaxActiveUploadsGlobal()
                || uploadRepository.countActiveByRobotId(robotId, UploadStatus.ACTIVE)
                        >= properties.getRecording().getMaxActiveUploadsPerRobot()) {
            throw error(HttpStatus.TOO_MANY_REQUESTS, "UPLOAD_CONCURRENCY_LIMIT", "Upload concurrency limit exceeded");
        }
    }

    private MediaRecordingUpload requireActiveUpload(String robotId, String uploadId) {
        MediaRecordingUpload upload = requireUpload(robotId, uploadId);
        if (upload.getStatus() != UploadStatus.ACTIVE || upload.getExpiresAt().isBefore(now())) {
            throw error(HttpStatus.CONFLICT, "UPLOAD_NOT_ACTIVE", "Upload session is no longer active");
        }
        return upload;
    }

    private MediaRecordingUpload requireUpload(String robotId, String uploadId) {
        MediaRecordingUpload upload = uploadRepository.findById(uploadId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "UPLOAD_NOT_FOUND", "Upload not found"));
        requireRobotRecording(robotId, upload.getRecordingId());
        return upload;
    }

    private MediaRecording requireRobotRecording(String robotId, String recordingId) {
        MediaRecording recording = requireRecording(recordingId);
        if (!recording.getRobotId().equals(robotId)) {
            throw error(HttpStatus.NOT_FOUND, "RECORDING_NOT_FOUND", "Recording not found");
        }
        return recording;
    }

    private MediaRecording requireRecording(String recordingId) {
        return recordingRepository.findById(recordingId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "RECORDING_NOT_FOUND", "Recording not found"));
    }

    private void refresh(MediaRecordingUpload upload) {
        if (properties.getRecording().isUploadSessionRefreshEnabled()) {
            upload.setExpiresAt(now().plusHours(properties.getRecording().getUploadExpireHours()));
        }
        upload.setLastActiveAt(now());
        uploadRepository.save(upload);
    }

    private RecordingStatusResponse status(MediaRecording recording) {
        return new RecordingStatusResponse(
                recording.getRecordingId(),
                recording.getStatus().name(),
                recording.getFileSize(),
                recording.getDurationSeconds(),
                recording.getStatus() == RecordingStatus.READY,
                recording.getErrorCode(),
                recording.getErrorMessage(),
                recording.getUploadedAt());
    }

    private RecordingListItemResponse item(MediaRecording recording) {
        return new RecordingListItemResponse(
                recording.getRecordingId(),
                recording.getRobotId(),
                recording.getDeviceId(),
                recording.getFileName(),
                recording.getFileSize(),
                recording.getDurationSeconds(),
                recording.getRecordedStartedAt(),
                recording.getStatus().name(),
                recording.getErrorCode(),
                recording.getUploadedAt());
    }

    private String objectKey(MediaRecording recording, OffsetDateTime time) {
        // 对象键包含 org/robot/device/date/recordingId，便于按机器人和日期做生命周期清理。
        return "recordings/%s/%s/%s/%04d/%02d/%02d/%s/original/source.mp4".formatted(
                recording.getOrgId(),
                recording.getRobotId(),
                recording.getDeviceId(),
                time.getYear(),
                time.getMonthValue(),
                time.getDayOfMonth(),
                recording.getRecordingId());
    }

    /**
     * 获取录像 HLS 资源对象前缀。
     *
     * @param recording 录像实体
     * @return HLS 资源对象前缀
     */
    public String hlsPrefix(MediaRecording recording) {
        return recording.getSourceObjectKey().replace("/original/source.mp4", "/hls/");
    }

    /**
     * 获取录像预览资源对象前缀。
     *
     * @param recording 录像实体
     * @return 预览资源对象前缀
     */
    public String previewPrefix(MediaRecording recording) {
        return recording.getSourceObjectKey().replace("/original/source.mp4", "/preview/");
    }

    private String playlistAssetName(MediaRecording recording) {
        String playlistKey = recording.getHlsPlaylistObjectKey();
        String prefix = hlsPrefix(recording);
        if (playlistKey != null && playlistKey.startsWith(prefix)) {
            return playlistKey.substring(prefix.length());
        }
        return "master.m3u8";
    }

    private String normalizeAssetName(String objectName) {
        String normalized = objectName == null ? "" : objectName;
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isBlank() || normalized.contains("..") || normalized.contains("//")) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_ASSET_NAME", "Invalid asset name");
        }
        return normalized;
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

    private String rewritePreviewCue(String line, String basePath, String encodedToken) {
        if (line.isBlank() || line.startsWith("WEBVTT") || line.contains("-->") || line.startsWith("#") || line.contains("://")) {
            return line;
        }
        int fragmentIndex = line.indexOf("#");
        String path = fragmentIndex >= 0 ? line.substring(0, fragmentIndex) : line;
        String fragment = fragmentIndex >= 0 ? line.substring(fragmentIndex) : "";
        return basePath + path + "?token=" + encodedToken + fragment;
    }

    private String signPlayback(String recordingId, long expiresAt) {
        String payload = recordingId + "." + expiresAt;
        return payload + "." + signature(payload);
    }

    private void verifyPlayback(String recordingId, String token) {
        if (token == null) {
            throw error(HttpStatus.FORBIDDEN, "PLAY_TOKEN_REQUIRED", "Playback token is required");
        }
        String[] values = token.split("\\.");
        if (values.length != 3 || !recordingId.equals(values[0])) {
            throw error(HttpStatus.FORBIDDEN, "INVALID_PLAY_TOKEN", "Playback token is invalid");
        }
        long expiry;
        try {
            expiry = Long.parseLong(values[1]);
        } catch (NumberFormatException ex) {
            throw error(HttpStatus.FORBIDDEN, "INVALID_PLAY_TOKEN", "Playback token is invalid");
        }
        String payload = values[0] + "." + values[1];
        // 使用常量时间比较签名，避免通过响应时间推测 HMAC 字符。
        if (expiry < now().toEpochSecond() || !constantEquals(values[2], signature(payload))) {
            throw error(HttpStatus.FORBIDDEN, "PLAY_TOKEN_EXPIRED", "Playback token expired or invalid");
        }
    }

    private String signature(String payload) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(
                    properties.getRecording().getPlayTokenSecret().getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to create playback token", ex);
        }
    }

    private boolean constantEquals(String left, String right) {
        return java.security.MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8));
    }

    private void requireRobotId(String robotId) {
        if (robotId == null || robotId.isBlank()) {
            throw error(HttpStatus.BAD_REQUEST, "ROBOT_ID_REQUIRED", "X-Robot-Id is required");
        }
    }

    private RecordingApiException error(HttpStatus status, String code, String message) {
        return new RecordingApiException(status, code, message);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private String id() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 播放或预览资源响应体。
     *
     * @param bytes 资源内容
     * @param contentType 资源媒体类型
     */
    public record PlaybackAsset(byte[] bytes, String contentType) {
    }
}
