package com.robot.mediaserver.video.service;

import com.robot.mediaserver.auth.CurrentUser;
import com.robot.mediaserver.config.DateTimeConfig;
import com.robot.mediaserver.storage.MinioStorageService;
import com.robot.mediaserver.video.dto.CompleteSnapshotRequest;
import com.robot.mediaserver.video.dto.CreateSnapshotRequest;
import com.robot.mediaserver.video.dto.FailSnapshotRequest;
import com.robot.mediaserver.video.dto.SnapshotListResponse;
import com.robot.mediaserver.video.dto.SnapshotResponse;
import com.robot.mediaserver.video.event.MediaEventLogService;
import com.robot.mediaserver.video.model.MediaSnapshot;
import com.robot.mediaserver.video.model.SnapshotStatus;
import com.robot.mediaserver.video.model.VideoSession;
import com.robot.mediaserver.video.repository.MediaSnapshotRepository;
import jakarta.transaction.Transactional;
import java.io.ByteArrayInputStream;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 抓拍任务服务。
 *
 * <p>当前服务先完成抓拍任务落库和状态事件通知。后续 Snapshot Worker 订阅
 * LiveKit Track 截帧后，通过本服务回写 COMPLETED/FAILED。</p>
 *
 * @author leelay
 * @date 2026/05/20
 */
@Service
public class SnapshotService {

    private final MediaSnapshotRepository repository;
    private final MediaEventLogService eventLogService;
    private final MinioStorageService minioStorageService;

    public SnapshotService(
            MediaSnapshotRepository repository,
            MediaEventLogService eventLogService,
            MinioStorageService minioStorageService) {
        this.repository = repository;
        this.eventLogService = eventLogService;
        this.minioStorageService = minioStorageService;
    }

    /**
     * 创建抓拍任务。
     *
     * @param session 实时视频会话
     * @param request 抓拍请求
     * @param user 当前用户
     * @return 抓拍响应
     */
    @Transactional
    public SnapshotResponse create(VideoSession session, CreateSnapshotRequest request, CurrentUser user) {
        OffsetDateTime now = now();
        MediaSnapshot snapshot = new MediaSnapshot();
        snapshot.setSnapshotId("snap_" + compactUuid());
        snapshot.setSessionId(session.getSessionId());
        snapshot.setTrackSid(resolveTrackSid(session, request));
        snapshot.setRobotId(session.getRobotId());
        snapshot.setDeviceId(session.getDeviceId());
        snapshot.setChannel(session.getChannel());
        snapshot.setQuality(session.getQuality());
        snapshot.setStatus(SnapshotStatus.PROCESSING);
        snapshot.setPreviewObjectKey(request.getClientPreviewObjectKey());
        snapshot.setPreviewImageHash(request.getPreviewImageHash());
        snapshot.setSource("livekit_track");
        snapshot.setReason(request.getReason());
        snapshot.setRemark(request.getRemark());
        snapshot.setCreatedBy(user.userId());
        snapshot.setClientCapturedAt(request.getClientCapturedAt());
        snapshot.setCreatedAt(now);
        snapshot.setUpdatedAt(now);
        repository.save(snapshot);

        eventLogService.recordAndPublish(session.getSessionId(), "snapshot.requested", Map.of(
                "snapshotId", snapshot.getSnapshotId(),
                "sessionId", snapshot.getSessionId(),
                "trackSid", snapshot.getTrackSid(),
                "source", snapshot.getSource(),
                "createdBy", snapshot.getCreatedBy()));
        return toResponse(snapshot);
    }

    /**
     * 查询指定会话最近抓拍任务。
     *
     * @param sessionId 会话 ID
     * @return 抓拍任务列表
     */
    public List<SnapshotResponse> recentBySession(String sessionId) {
        return repository.findTop20BySessionIdOrderByCreatedAtDesc(sessionId).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<SnapshotResponse> recentByRobotDevice(String robotId, String deviceId) {
        return repository.findTop50ByRobotIdAndDeviceIdOrderByCreatedAtDesc(robotId, deviceId).stream()
                .map(this::toResponse)
                .toList();
    }

    public SnapshotListResponse list(String robotId, String deviceId, int page, int pageSize) {
        int safePage = Math.max(page, 0);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        Specification<MediaSnapshot> specification = (root, query, builder) -> {
            var predicate = builder.conjunction();
            if (robotId != null && !robotId.isBlank()) {
                predicate = builder.and(predicate, builder.equal(root.get("robotId"), robotId));
            }
            if (deviceId != null && !deviceId.isBlank()) {
                predicate = builder.and(predicate, builder.equal(root.get("deviceId"), deviceId));
            }
            return predicate;
        };
        Page<MediaSnapshot> result = repository.findAll(
                specification,
                PageRequest.of(safePage, safePageSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        return new SnapshotListResponse(
                result.getContent().stream().map(this::toResponse).toList(),
                safePage,
                safePageSize,
                result.getTotalElements());
    }

    /**
     * 完成抓拍任务。
     *
     * <p>Snapshot Worker 可直接传 objectKey，也可上传文件由本服务写入 MinIO。</p>
     *
     * @param snapshotId 抓拍任务 ID
     * @param request 完成请求
     * @param file 抓拍图片文件
     * @return 抓拍响应
     */
    @Transactional
    public SnapshotResponse complete(String snapshotId, CompleteSnapshotRequest request, MultipartFile file) {
        MediaSnapshot snapshot = requireSnapshot(snapshotId);
        String objectKey = request.getOfficialObjectKey();
        if ((objectKey == null || objectKey.isBlank()) && file != null && !file.isEmpty()) {
            objectKey = objectKey(snapshot, file.getOriginalFilename());
            uploadSnapshotFile(objectKey, file);
        }
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("officialObjectKey or file is required");
        }
        snapshot.setStatus(SnapshotStatus.COMPLETED);
        snapshot.setOfficialObjectKey(objectKey);
        snapshot.setOfficialCapturedAt(request.getOfficialCapturedAt() == null ? now() : request.getOfficialCapturedAt());
        snapshot.setTimeDeltaMs(request.getTimeDeltaMs());
        snapshot.setUpdatedAt(now());
        repository.save(snapshot);
        eventLogService.recordAndPublish(snapshot.getSessionId(), "snapshot.completed", Map.of(
                "snapshotId", snapshot.getSnapshotId(),
                "officialObjectKey", snapshot.getOfficialObjectKey(),
                "capturedAt", DateTimeConfig.format(snapshot.getOfficialCapturedAt()),
                "source", snapshot.getSource()));
        return toResponse(snapshot);
    }

    @Transactional
    public SnapshotResponse completeBytes(String snapshotId, byte[] bytes, OffsetDateTime capturedAt) {
        MediaSnapshot snapshot = requireSnapshot(snapshotId);
        String objectKey = objectKey(snapshot, "snapshot.jpg");
        minioStorageService.upload(objectKey, new ByteArrayInputStream(bytes), bytes.length, "image/jpeg");
        snapshot.setStatus(SnapshotStatus.COMPLETED);
        snapshot.setOfficialObjectKey(objectKey);
        snapshot.setOfficialCapturedAt(capturedAt == null ? now() : capturedAt);
        if (snapshot.getClientCapturedAt() != null) {
            snapshot.setTimeDeltaMs(snapshot.getOfficialCapturedAt().toInstant().toEpochMilli()
                    - snapshot.getClientCapturedAt().toInstant().toEpochMilli());
        }
        snapshot.setUpdatedAt(now());
        repository.save(snapshot);
        eventLogService.recordAndPublish(snapshot.getSessionId(), "snapshot.completed", Map.of(
                "snapshotId", snapshot.getSnapshotId(),
                "officialObjectKey", snapshot.getOfficialObjectKey(),
                "capturedAt", DateTimeConfig.format(snapshot.getOfficialCapturedAt()),
                "source", snapshot.getSource()));
        return toResponse(snapshot);
    }

    @Transactional
    public SnapshotResponse fail(String snapshotId, FailSnapshotRequest request) {
        MediaSnapshot snapshot = requireSnapshot(snapshotId);
        snapshot.setStatus(SnapshotStatus.FAILED);
        snapshot.setErrorCode(request.getErrorCode());
        snapshot.setErrorMessage(request.getErrorMessage());
        snapshot.setUpdatedAt(now());
        repository.save(snapshot);
        eventLogService.recordAndPublish(snapshot.getSessionId(), "snapshot.failed", Map.of(
                "snapshotId", snapshot.getSnapshotId(),
                "errorCode", safeValue(snapshot.getErrorCode()),
                "message", safeValue(snapshot.getErrorMessage())));
        return toResponse(snapshot);
    }

    public byte[] image(String snapshotId) {
        MediaSnapshot snapshot = requireSnapshot(snapshotId);
        if (snapshot.getOfficialObjectKey() == null || snapshot.getOfficialObjectKey().isBlank()) {
            throw new IllegalStateException("Snapshot image is not ready");
        }
        return minioStorageService.readObject(snapshot.getOfficialObjectKey());
    }

    private SnapshotResponse toResponse(MediaSnapshot snapshot) {
        return new SnapshotResponse(
                snapshot.getSnapshotId(),
                snapshot.getSessionId(),
                snapshot.getRobotId(),
                snapshot.getDeviceId(),
                snapshot.getChannel() == null ? null : snapshot.getChannel().name(),
                snapshot.getQuality() == null ? null : snapshot.getQuality().name(),
                snapshot.getStatus().name(),
                snapshot.getSource(),
                snapshot.getPreviewObjectKey() != null,
                snapshot.getOfficialObjectKey(),
                snapshot.getPreviewObjectKey(),
                snapshot.getErrorCode(),
                snapshot.getErrorMessage(),
                snapshot.getOfficialCapturedAt(),
                snapshot.getCreatedAt());
    }

    private MediaSnapshot requireSnapshot(String snapshotId) {
        return repository.findById(snapshotId)
                .orElseThrow(() -> new IllegalArgumentException("Snapshot not found: " + snapshotId));
    }

    private String resolveTrackSid(VideoSession session, CreateSnapshotRequest request) {
        if (request.getTrackSid() != null && !request.getTrackSid().isBlank()) {
            return request.getTrackSid();
        }
        return session.getTrackSid();
    }

    private void uploadSnapshotFile(String objectKey, MultipartFile file) {
        try {
            minioStorageService.upload(objectKey, file.getInputStream(), file.getSize(), contentType(file));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to upload snapshot file", ex);
        }
    }

    private String objectKey(MediaSnapshot snapshot, String originalFilename) {
        String suffix = extension(originalFilename);
        OffsetDateTime now = now();
        return "snapshots/%s/%s/%04d/%02d/%02d/%s%s".formatted(
                safePath(snapshot.getRobotId()),
                safePath(snapshot.getDeviceId()),
                now.getYear(),
                now.getMonthValue(),
                now.getDayOfMonth(),
                snapshot.getSnapshotId(),
                suffix);
    }

    private String extension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return ".jpg";
        }
        String suffix = filename.substring(filename.lastIndexOf('.'));
        return suffix.length() > 10 ? ".jpg" : suffix;
    }

    private String contentType(MultipartFile file) {
        return file.getContentType() == null ? "image/jpeg" : file.getContentType();
    }

    private String safeValue(String value) {
        return value == null ? "" : value;
    }

    private String safePath(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private String compactUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
