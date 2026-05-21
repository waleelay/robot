package com.robot.mediaserver.video.service;

import com.robot.mediaserver.auth.CurrentUser;
import com.robot.mediaserver.config.MediaProperties;
import com.robot.mediaserver.livekit.LiveKitRoomService;
import com.robot.mediaserver.livekit.LiveKitTokenService;
import com.robot.mediaserver.livekit.LiveKitTokenService.TokenResult;
import com.robot.mediaserver.source.service.MediaSourceService;
import com.robot.mediaserver.video.dto.CreateSnapshotRequest;
import com.robot.mediaserver.video.dto.CreateVideoSessionRequest;
import com.robot.mediaserver.video.dto.SnapshotResponse;
import com.robot.mediaserver.video.dto.SwitchChannelRequest;
import com.robot.mediaserver.video.dto.VideoSessionResponse;
import com.robot.mediaserver.video.dto.ViewerTokenResponse;
import com.robot.mediaserver.video.event.MediaEventLogService;
import com.robot.mediaserver.video.messaging.RobotMediaCommandService;
import com.robot.mediaserver.video.messaging.VideoStartCommand;
import com.robot.mediaserver.video.model.MediaSessionViewer;
import com.robot.mediaserver.video.model.VideoSession;
import com.robot.mediaserver.video.model.VideoSessionStatus;
import com.robot.mediaserver.video.repository.MediaSessionViewerRepository;
import com.robot.mediaserver.video.repository.VideoSessionRepository;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 实时视频会话编排服务。
 *
 * <p>该服务负责把平台侧一次“开始观看”请求编排为：会话落库、LiveKit Token 生成、
 * MQTT 启动指令下发、WebSocket 状态推送。真实媒体流不经过本服务传输，而是由
 * 云接入客户端发布到 LiveKit，再由前端订阅。</p>
 *
 * @author leelay
 * @date 2026/05/19
 */
@Service
public class VideoSessionService {

    /**
     * 可复用状态集合。
     *
     * <p>同一 robotId + deviceId + channel + quality 已经存在可复用会话时，
     * 新观看者只增加 viewerCount 并复用原 Room/Track，避免重复下发 start 指令。</p>
     */
    private static final Set<VideoSessionStatus> REUSABLE_STATUSES = Set.of(
            VideoSessionStatus.ROOM_READY,
            VideoSessionStatus.STREAMING,
            VideoSessionStatus.IDLE_WAIT);

    private static final Set<VideoSessionStatus> LIVEKIT_EVENT_STATUSES = Set.of(
            VideoSessionStatus.REQUESTING_CLIENT,
            VideoSessionStatus.CLIENT_ACKED,
            VideoSessionStatus.ROOM_READY,
            VideoSessionStatus.STREAMING,
            VideoSessionStatus.INTERRUPTED,
            VideoSessionStatus.IDLE_WAIT);

    private final VideoSessionRepository repository;
    private final MediaSessionViewerRepository viewerRepository;
    private final LiveKitRoomService liveKitRoomService;
    private final LiveKitTokenService liveKitTokenService;
    private final RobotMediaCommandService commandService;
    private final MediaEventLogService eventLogService;
    private final SnapshotService snapshotService;
    private final MediaTrackService mediaTrackService;
    private final MediaSourceService mediaSourceService;
    private final MediaProperties properties;

    public VideoSessionService(
            VideoSessionRepository repository,
            MediaSessionViewerRepository viewerRepository,
            LiveKitRoomService liveKitRoomService,
            LiveKitTokenService liveKitTokenService,
            RobotMediaCommandService commandService,
            MediaEventLogService eventLogService,
            SnapshotService snapshotService,
            MediaTrackService mediaTrackService,
            MediaSourceService mediaSourceService,
            MediaProperties properties) {
        this.repository = repository;
        this.viewerRepository = viewerRepository;
        this.liveKitRoomService = liveKitRoomService;
        this.liveKitTokenService = liveKitTokenService;
        this.commandService = commandService;
        this.eventLogService = eventLogService;
        this.snapshotService = snapshotService;
        this.mediaTrackService = mediaTrackService;
        this.mediaSourceService = mediaSourceService;
        this.properties = properties;
    }

    /**
     * 创建或复用实时视频会话。
     *
     * <p>主流程：先检查可复用会话；若不存在则创建业务会话、生成机器人端发布 Token、
     * 下发 MQTT start 指令，并返回前端观看 Token。</p>
     */
    @Transactional
    public VideoSessionResponse create(CreateVideoSessionRequest request, CurrentUser user) {
        if (request.isReuse()) {
            var existing = repository.findFirstByRobotIdAndDeviceIdAndChannelAndQualityAndStatusInOrderByCreatedAtDesc(
                    request.getRobotId(),
                    request.getDeviceId(),
                    request.getChannel(),
                    request.getQuality(),
                    REUSABLE_STATUSES);
            if (existing.isPresent()) {
                VideoSession session = existing.get();
                addViewer(session, user);
                session.setIdleSince(null);
                if (session.getStatus() == VideoSessionStatus.IDLE_WAIT) {
                    session.setStatus(VideoSessionStatus.STREAMING);
                }
                session.setViewerCount(activeViewerCount(session.getSessionId()));
                session.setUpdatedAt(now());
                repository.save(session);
                TokenResult viewerToken = liveKitTokenService.createViewerToken(session.getRoomName(), user.userId());
                emit("video.session.reused", session);
                return VideoSessionResponse.from(session, properties.getLivekit().getUrl(), viewerToken.token());
            }
        }

        VideoSession session = new VideoSession();
        session.setSessionId("vs_" + compactUuid());
        session.setRobotId(request.getRobotId());
        session.setDeviceId(request.getDeviceId());
        session.setChannel(request.getChannel());
        session.setQuality(request.getQuality());
        session.setRoomName(roomName(request));
        session.setStatus(VideoSessionStatus.INIT);
        session.setViewerCount(1);
        session.setOrgId(user.orgId());
        session.setCreatedBy(user.userId());
        session.setCreatedAt(now());
        session.setUpdatedAt(now());
        repository.save(session);
        addViewer(session, user);
        session.setViewerCount(activeViewerCount(session.getSessionId()));
        repository.save(session);
        emit("video.session.created", session);
        liveKitRoomService.createRoom(session.getRoomName());
        emit("video.room.ready", Map.of(
                "sessionId", session.getSessionId(),
                "roomName", session.getRoomName()));

        // 发布 Token 只允许云接入客户端发布本机器人对应 Track，不允许订阅其他视频。
        TokenResult publisherToken = liveKitTokenService.createPublisherToken(
                session.getRoomName(), session.getRobotId(), session.getDeviceId());
        String commandId = "cmd_" + compactUuid();
        session.setCommandId(commandId);
        session.setCommandRequestedAt(now());
        transition(session, VideoSessionStatus.REQUESTING_CLIENT, "video.client.requested", Map.of(
                "sessionId", session.getSessionId(),
                "commandId", commandId,
                "timeoutSeconds", properties.getSession().getClientAckTimeoutSeconds()));
        session.setUpdatedAt(now());
        repository.save(session);

        commandService.sendStart(new VideoStartCommand(
                commandId,
                session.getSessionId(),
                session.getRobotId(),
                session.getDeviceId(),
                session.getChannel(),
                session.getQuality(),
                properties.getLivekit().getUrl(),
                session.getRoomName(),
                publisherToken.token(),
                "robot:" + session.getRobotId() + ":" + session.getDeviceId(),
                mediaSourceService.rtspUrl(session.getRobotId(), session.getDeviceId(), session.getChannel(), session.getQuality()),
                publisherToken.expiresAt()));

        TokenResult viewerToken = liveKitTokenService.createViewerToken(session.getRoomName(), user.userId());
        return VideoSessionResponse.from(session, properties.getLivekit().getUrl(), viewerToken.token());
    }

    public VideoSessionResponse get(String sessionId, CurrentUser user) {
        VideoSession session = requireSession(sessionId);
        TokenResult viewerToken = liveKitTokenService.createViewerToken(session.getRoomName(), user.userId());
        return VideoSessionResponse.from(session, properties.getLivekit().getUrl(), viewerToken.token());
    }

    public ViewerTokenResponse createViewerToken(String sessionId, CurrentUser user) {
        VideoSession session = requireSession(sessionId);
        TokenResult token = liveKitTokenService.createViewerToken(session.getRoomName(), user.userId());
        return new ViewerTokenResponse(properties.getLivekit().getUrl(), session.getRoomName(), token.token(), token.expiresAt());
    }

    @Transactional
    public VideoSessionResponse stop(String sessionId, CurrentUser user) {
        VideoSession session = requireSession(sessionId);
        removeViewer(sessionId, user);
        session.setViewerCount(activeViewerCount(sessionId));
        if (session.getViewerCount() == 0) {
            session.setIdleSince(now());
            transition(session, VideoSessionStatus.IDLE_WAIT, "video.session.idle_wait", Map.of(
                    "sessionId", session.getSessionId(),
                    "idleReleaseDelaySeconds", properties.getSession().getIdleReleaseDelaySeconds()));
        } else {
            emit("video.viewer.changed", session);
        }
        session.setUpdatedAt(now());
        repository.save(session);
        return VideoSessionResponse.from(session, properties.getLivekit().getUrl(), null);
    }

    @Transactional
    public VideoSessionResponse switchChannel(String sessionId, SwitchChannelRequest request) {
        VideoSession session = requireSession(sessionId);
        session.setChannel(request.getChannel());
        if (request.getQuality() != null) {
            session.setQuality(request.getQuality());
        }
        session.setRoomName("media." + session.getRobotId() + "." + session.getDeviceId() + "." + session.getChannel());
        liveKitRoomService.createRoom(session.getRoomName());
        TokenResult publisherToken = liveKitTokenService.createPublisherToken(
                session.getRoomName(), session.getRobotId(), session.getDeviceId());
        String commandId = "cmd_" + compactUuid();
        session.setCommandId(commandId);
        session.setCommandRequestedAt(now());
        transition(session, VideoSessionStatus.REQUESTING_CLIENT, "video.track.switching", session);
        session.setUpdatedAt(now());
        repository.save(session);
        commandService.sendSwitchChannel(session.getRobotId(), new VideoStartCommand(
                commandId,
                session.getSessionId(),
                session.getRobotId(),
                session.getDeviceId(),
                session.getChannel(),
                session.getQuality(),
                properties.getLivekit().getUrl(),
                session.getRoomName(),
                publisherToken.token(),
                "robot:" + session.getRobotId() + ":" + session.getDeviceId(),
                mediaSourceService.rtspUrl(session.getRobotId(), session.getDeviceId(), session.getChannel(), session.getQuality()),
                publisherToken.expiresAt()));
        return VideoSessionResponse.from(session, properties.getLivekit().getUrl(), null);
    }

    public SnapshotResponse createSnapshot(String sessionId, CreateSnapshotRequest request, CurrentUser user) {
        VideoSession session = requireSession(sessionId);
        if (session.getStatus() != VideoSessionStatus.STREAMING && session.getStatus() != VideoSessionStatus.ROOM_READY) {
            throw new IllegalStateException("Current session is not streaming");
        }
        return snapshotService.create(session, request, user);
    }

    @Transactional
    public VideoSessionResponse markClientAcked(String sessionId) {
        VideoSession session = requireSession(sessionId);
        transition(session, VideoSessionStatus.CLIENT_ACKED, "video.client.acked", session);
        session.setUpdatedAt(now());
        repository.save(session);
        return VideoSessionResponse.from(session, properties.getLivekit().getUrl(), null);
    }

    @Transactional
    public VideoSessionResponse markTrackPublished(String sessionId, String trackSid) {
        VideoSession session = requireSession(sessionId);
        session.setTrackSid(trackSid);
        session.setTrackName("video." + session.getChannel() + "." + session.getQuality());
        mediaTrackService.publish(session, trackSid, session.getTrackName());
        session.setStartedAt(now());
        transition(session, VideoSessionStatus.STREAMING, "video.track.published", session);
        session.setUpdatedAt(now());
        repository.save(session);
        emit("video.session.streaming", session);
        return VideoSessionResponse.from(session, properties.getLivekit().getUrl(), null);
    }

    @Transactional
    public void handleClientAck(String sessionId, boolean success, String message) {
        VideoSession session = requireSession(sessionId);
        if (success) {
            session.setLastStatusAt(now());
            transition(session, VideoSessionStatus.CLIENT_ACKED, "video.client.acked", Map.of(
                    "sessionId", sessionId,
                    "message", safeMessage(message)));
        } else {
            markFailed(session, "CLIENT_ACK_FAILED", safeMessage(message), "video.session.failed");
        }
        session.setUpdatedAt(now());
        repository.save(session);
    }

    @Transactional
    public void handleClientStatus(
            String sessionId,
            String status,
            String trackSid,
            String trackName,
            String errorCode,
            String message) {
        VideoSession session = requireSession(sessionId);
        session.setLastStatusAt(now());
        String normalized = status == null ? "" : status.trim().toLowerCase();
        switch (normalized) {
            case "ack", "acked" -> transition(session, VideoSessionStatus.CLIENT_ACKED, "video.client.acked", session);
            case "room_ready", "publishing" -> transition(session, VideoSessionStatus.ROOM_READY, "video.room.ready", session);
            case "streaming", "track_published" -> {
                if (trackSid != null && !trackSid.isBlank()) {
                    session.setTrackSid(trackSid);
                }
                session.setTrackName(trackName == null || trackName.isBlank()
                        ? "video." + session.getChannel() + "." + session.getQuality()
                        : trackName);
                mediaTrackService.publish(session, session.getTrackSid(), session.getTrackName());
                session.setStartedAt(session.getStartedAt() == null ? now() : session.getStartedAt());
                transition(session, VideoSessionStatus.STREAMING, "video.session.streaming", session);
            }
            case "interrupted" -> transition(session, VideoSessionStatus.INTERRUPTED, "video.session.interrupted", Map.of(
                    "sessionId", sessionId,
                    "message", safeMessage(message)));
            case "stopped", "closed" -> {
                session.setEndedAt(now());
                transition(session, VideoSessionStatus.CLOSED, "video.session.closed", session);
            }
            case "failed", "error" -> markFailed(session, errorCode == null ? "CLIENT_STATUS_FAILED" : errorCode, safeMessage(message), "video.session.failed");
            default -> eventLogService.recordAndPublish(sessionId, "video.client.status", Map.of(
                    "sessionId", sessionId,
                    "status", safeMessage(status),
                    "message", safeMessage(message)));
        }
        session.setUpdatedAt(now());
        repository.save(session);
    }

    @Transactional
    public void handleClientOnline(String robotId, String status) {
        if (!"online".equalsIgnoreCase(status)) {
            return;
        }
        repository.findByRobotIdAndViewerCountGreaterThanAndStatusInOrderByUpdatedAtDesc(
                        robotId,
                        0,
                        Set.of(VideoSessionStatus.INTERRUPTED, VideoSessionStatus.FAILED, VideoSessionStatus.TIMEOUT))
                .forEach(session -> restartSession(session, "video.client.online_restart"));
    }

    @Transactional
    public VideoSessionResponse restartSession(String sessionId, CurrentUser user) {
        VideoSession session = requireSession(sessionId);
        addViewer(session, user);
        session.setViewerCount(activeViewerCount(sessionId));
        restartSession(session, "video.session.restart");
        return VideoSessionResponse.from(session, properties.getLivekit().getUrl(), null);
    }

    @Transactional
    public void restartSession(String sessionId) {
        restartSession(requireSession(sessionId), "video.session.auto_restart");
    }

    @Transactional
    public void handleLiveKitTrackPublished(String roomName, String trackSid, String trackName) {
        VideoSession session = requireSessionByRoomName(roomName);
        session.setTrackSid(trackSid);
        session.setTrackName(trackName == null || trackName.isBlank()
                ? "video." + session.getChannel() + "." + session.getQuality()
                : trackName);
        mediaTrackService.publish(session, session.getTrackSid(), session.getTrackName());
        session.setStartedAt(session.getStartedAt() == null ? now() : session.getStartedAt());
        session.setLastStatusAt(now());
        transition(session, VideoSessionStatus.STREAMING, "video.track.published", session);
        emit("video.session.streaming", session);
        repository.save(session);
    }

    @Transactional
    public void handleLiveKitTrackInterrupted(String roomName, String message) {
        VideoSession session = requireSessionByRoomName(roomName);
        mediaTrackService.unpublish(session);
        session.setLastStatusAt(now());
        transition(session, VideoSessionStatus.INTERRUPTED, "video.session.interrupted", Map.of(
                "sessionId", session.getSessionId(),
                "roomName", roomName,
                "message", safeMessage(message)));
        repository.save(session);
    }

    private void restartSession(VideoSession session, String event) {
        if (session.getViewerCount() <= 0) {
            return;
        }
        liveKitRoomService.createRoom(session.getRoomName());
        TokenResult publisherToken = liveKitTokenService.createPublisherToken(
                session.getRoomName(), session.getRobotId(), session.getDeviceId());
        String commandId = "cmd_" + compactUuid();
        session.setCommandId(commandId);
        session.setCommandRequestedAt(now());
        session.setEndedAt(null);
        session.setLastErrorCode(null);
        session.setLastErrorMessage(null);
        transition(session, VideoSessionStatus.REQUESTING_CLIENT, event, Map.of(
                "sessionId", session.getSessionId(),
                "commandId", commandId));
        repository.save(session);
        commandService.sendStart(new VideoStartCommand(
                commandId,
                session.getSessionId(),
                session.getRobotId(),
                session.getDeviceId(),
                session.getChannel(),
                session.getQuality(),
                properties.getLivekit().getUrl(),
                session.getRoomName(),
                publisherToken.token(),
                "robot:" + session.getRobotId() + ":" + session.getDeviceId(),
                mediaSourceService.rtspUrl(session.getRobotId(), session.getDeviceId(), session.getChannel(), session.getQuality()),
                publisherToken.expiresAt()));
    }

    @Transactional
    public void handleLiveKitRoomFinished(String roomName) {
        VideoSession session = requireSessionByRoomName(roomName);
        mediaTrackService.unpublish(session);
        session.setEndedAt(now());
        transition(session, VideoSessionStatus.CLOSED, "video.session.closed", session);
        repository.save(session);
    }

    @Transactional
    public void markTimeout(String sessionId, String errorCode, String message) {
        VideoSession session = requireSession(sessionId);
        session.setEndedAt(now());
        markFailed(session, errorCode, message, "video.session.failed");
        session.setUpdatedAt(now());
        repository.save(session);
    }

    @Transactional
    public void releaseIdleSession(String sessionId) {
        VideoSession session = requireSession(sessionId);
        if (session.getStatus() != VideoSessionStatus.IDLE_WAIT || session.getViewerCount() > 0) {
            return;
        }
        transition(session, VideoSessionStatus.STOPPING, "video.session.stopping", session);
        mediaTrackService.unpublish(session);
        commandService.sendStop(session.getRobotId(), Map.of(
                "sessionId", session.getSessionId(),
                "commandId", "cmd_" + compactUuid(),
                "roomName", session.getRoomName()));
        liveKitRoomService.deleteRoom(session.getRoomName());
        session.setEndedAt(now());
        transition(session, VideoSessionStatus.CLOSED, "video.session.closed", session);
        repository.save(session);
    }

    public List<VideoSessionResponse> recent(CurrentUser user) {
        return repository.findTop20ByCreatedByOrderByCreatedAtDesc(user.userId()).stream()
                .map(session -> VideoSessionResponse.from(session, properties.getLivekit().getUrl(), null))
                .toList();
    }

    public List<VideoSessionResponse> active() {
        return repository.findTop16ByStatusInOrderByUpdatedAtDesc(REUSABLE_STATUSES).stream()
                .map(session -> VideoSessionResponse.from(session, properties.getLivekit().getUrl(), null))
                .toList();
    }

    private VideoSession requireSession(String sessionId) {
        return repository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Video session not found: " + sessionId));
    }

    private VideoSession requireSessionByRoomName(String roomName) {
        return repository.findFirstByRoomNameAndStatusInOrderByCreatedAtDesc(roomName, LIVEKIT_EVENT_STATUSES)
                .orElseThrow(() -> new IllegalArgumentException("Video session not found by roomName: " + roomName));
    }

    private String roomName(CreateVideoSessionRequest request) {
        return "media." + request.getRobotId() + "." + request.getDeviceId() + "." + request.getChannel();
    }

    private void emit(String event, Object data) {
        eventLogService.recordAndPublish(resolveSessionId(data), event, data);
    }

    private void transition(VideoSession session, VideoSessionStatus targetStatus, String event, Object payload) {
        session.setStatus(targetStatus);
        session.setUpdatedAt(now());
        eventLogService.recordAndPublish(session.getSessionId(), event, payload);
    }

    private void markFailed(VideoSession session, String errorCode, String message, String event) {
        session.setStatus(VideoSessionStatus.FAILED);
        session.setLastErrorCode(errorCode);
        session.setLastErrorMessage(message);
        eventLogService.recordAndPublish(session.getSessionId(), event, Map.of(
                "sessionId", session.getSessionId(),
                "errorCode", errorCode,
                "message", message));
    }

    private String resolveSessionId(Object data) {
        if (data instanceof VideoSession session) {
            return session.getSessionId();
        }
        if (data instanceof Map<?, ?> map && map.get("sessionId") != null) {
            return String.valueOf(map.get("sessionId"));
        }
        return null;
    }

    private String safeMessage(String message) {
        return message == null ? "" : message;
    }

    private void addViewer(VideoSession session, CurrentUser user) {
        viewerRepository.findFirstBySessionIdAndUserIdAndLeftAtIsNull(session.getSessionId(), user.userId())
                .orElseGet(() -> {
                    MediaSessionViewer viewer = new MediaSessionViewer();
                    viewer.setId("viewer_" + compactUuid());
                    viewer.setSessionId(session.getSessionId());
                    viewer.setUserId(user.userId());
                    viewer.setOrgId(user.orgId());
                    viewer.setParticipantIdentity("user:" + user.userId() + ":web");
                    viewer.setClientType("web");
                    viewer.setJoinedAt(now());
                    return viewerRepository.save(viewer);
                });
    }

    private void removeViewer(String sessionId, CurrentUser user) {
        viewerRepository.findFirstBySessionIdAndUserIdAndLeftAtIsNull(sessionId, user.userId())
                .ifPresent(viewer -> {
                    viewer.setLeftAt(now());
                    viewerRepository.save(viewer);
                });
    }

    private int activeViewerCount(String sessionId) {
        return Math.toIntExact(viewerRepository.countBySessionIdAndLeftAtIsNull(sessionId));
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private String compactUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
