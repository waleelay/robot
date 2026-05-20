package com.robot.mediaserver.video.service;

import com.robot.mediaserver.auth.CurrentUser;
import com.robot.mediaserver.video.dto.CreateSnapshotRequest;
import com.robot.mediaserver.video.dto.SnapshotResponse;
import com.robot.mediaserver.video.event.MediaEventLogService;
import com.robot.mediaserver.video.model.MediaSnapshot;
import com.robot.mediaserver.video.model.SnapshotStatus;
import com.robot.mediaserver.video.model.VideoSession;
import com.robot.mediaserver.video.repository.MediaSnapshotRepository;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

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

    public SnapshotService(MediaSnapshotRepository repository, MediaEventLogService eventLogService) {
        this.repository = repository;
        this.eventLogService = eventLogService;
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
        snapshot.setTrackSid(request.getTrackSid());
        snapshot.setRobotId(session.getRobotId());
        snapshot.setDeviceId(session.getDeviceId());
        snapshot.setChannel(session.getChannel());
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

    private SnapshotResponse toResponse(MediaSnapshot snapshot) {
        return new SnapshotResponse(
                snapshot.getSnapshotId(),
                snapshot.getStatus().name(),
                snapshot.getSource(),
                snapshot.getPreviewObjectKey() != null,
                snapshot.getOfficialObjectKey(),
                snapshot.getPreviewObjectKey(),
                snapshot.getErrorCode(),
                snapshot.getErrorMessage(),
                snapshot.getCreatedAt());
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private String compactUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
