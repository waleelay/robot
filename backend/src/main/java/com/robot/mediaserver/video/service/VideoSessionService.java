package com.robot.mediaserver.video.service;

import com.robot.mediaserver.auth.CurrentUser;
import com.robot.mediaserver.config.MediaProperties;
import com.robot.mediaserver.livekit.LiveKitTokenService;
import com.robot.mediaserver.livekit.LiveKitTokenService.TokenResult;
import com.robot.mediaserver.video.dto.CreateSnapshotRequest;
import com.robot.mediaserver.video.dto.CreateVideoSessionRequest;
import com.robot.mediaserver.video.dto.SnapshotResponse;
import com.robot.mediaserver.video.dto.SwitchChannelRequest;
import com.robot.mediaserver.video.dto.VideoSessionResponse;
import com.robot.mediaserver.video.dto.ViewerTokenResponse;
import com.robot.mediaserver.video.messaging.RobotMediaCommandService;
import com.robot.mediaserver.video.messaging.VideoStartCommand;
import com.robot.mediaserver.video.model.VideoSession;
import com.robot.mediaserver.video.model.VideoSessionStatus;
import com.robot.mediaserver.video.repository.VideoSessionRepository;
import com.robot.mediaserver.ws.MediaWebSocketPublisher;
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
            VideoSessionStatus.REQUESTING_CLIENT,
            VideoSessionStatus.CLIENT_ACKED,
            VideoSessionStatus.ROOM_READY,
            VideoSessionStatus.STREAMING,
            VideoSessionStatus.INTERRUPTED,
            VideoSessionStatus.IDLE_WAIT);

    private final VideoSessionRepository repository;
    private final LiveKitTokenService liveKitTokenService;
    private final RobotMediaCommandService commandService;
    private final MediaWebSocketPublisher webSocketPublisher;
    private final MediaProperties properties;

    public VideoSessionService(
            VideoSessionRepository repository,
            LiveKitTokenService liveKitTokenService,
            RobotMediaCommandService commandService,
            MediaWebSocketPublisher webSocketPublisher,
            MediaProperties properties) {
        this.repository = repository;
        this.liveKitTokenService = liveKitTokenService;
        this.commandService = commandService;
        this.webSocketPublisher = webSocketPublisher;
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
                session.setViewerCount(session.getViewerCount() + 1);
                session.setUpdatedAt(now());
                repository.save(session);
                TokenResult viewerToken = liveKitTokenService.createViewerToken(session.getRoomName(), user.userId());
                publish("video.session.reused", session);
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
        publish("video.session.created", session);

        // 发布 Token 只允许云接入客户端发布本机器人对应 Track，不允许订阅其他视频。
        TokenResult publisherToken = liveKitTokenService.createPublisherToken(
                session.getRoomName(), session.getRobotId(), session.getDeviceId());
        String commandId = "cmd_" + compactUuid();
        session.setCommandId(commandId);
        session.setStatus(VideoSessionStatus.REQUESTING_CLIENT);
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
                publisherToken.expiresAt()));
        publish("video.client.requested", Map.of(
                "sessionId", session.getSessionId(),
                "commandId", commandId,
                "timeoutSeconds", properties.getSession().getClientAckTimeoutSeconds()));

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

    /**
     * 停止当前用户观看。
     *
     * <p>多人观看同一路时只减少 viewerCount；最后一个观看者退出时才下发 stop，
     * 释放云接入客户端的媒体资源。</p>
     */
    @Transactional
    public VideoSessionResponse stop(String sessionId, CurrentUser user) {
        VideoSession session = requireSession(sessionId);
        session.setViewerCount(Math.max(0, session.getViewerCount() - 1));
        if (session.getViewerCount() == 0) {
            session.setStatus(VideoSessionStatus.STOPPING);
            session.setEndedAt(now());
            commandService.sendStop(session.getRobotId(), Map.of(
                    "sessionId", session.getSessionId(),
                    "commandId", "cmd_" + compactUuid(),
                    "roomName", session.getRoomName()));
            publish("video.session.stopping", session);
            session.setStatus(VideoSessionStatus.CLOSED);
            publish("video.session.closed", session);
        } else {
            publish("video.viewer.changed", session);
        }
        session.setUpdatedAt(now());
        repository.save(session);
        return VideoSessionResponse.from(session, properties.getLivekit().getUrl(), null);
    }

    /**
     * 切换双光云台媒体通道。
     *
     * <p>该方法只处理媒体通道 visible/thermal/fusion 的切换。云台 PTZ、变焦、
     * 预置位等动作仍归 Robot Control Service。</p>
     */
    @Transactional
    public VideoSessionResponse switchChannel(String sessionId, SwitchChannelRequest request) {
        VideoSession session = requireSession(sessionId);
        session.setChannel(request.getChannel());
        if (request.getQuality() != null) {
            session.setQuality(request.getQuality());
        }
        session.setRoomName("media." + session.getRobotId() + "." + session.getDeviceId() + "." + session.getChannel());
        session.setStatus(VideoSessionStatus.REQUESTING_CLIENT);
        session.setUpdatedAt(now());
        repository.save(session);
        commandService.sendSwitchChannel(session.getRobotId(), Map.of(
                "sessionId", session.getSessionId(),
                "commandId", "cmd_" + compactUuid(),
                "channel", session.getChannel(),
                "quality", session.getQuality(),
                "roomName", session.getRoomName()));
        publish("video.track.switching", session);
        return VideoSessionResponse.from(session, properties.getLivekit().getUrl(), null);
    }

    /**
     * 创建抓拍任务。
     *
     * <p>当前版本先创建 PROCESSING 响应并推送 snapshot.requested。后续 Snapshot Worker
     * 会订阅对应 LiveKit Track，生成正式图片并写入 MinIO 和 media_snapshot。</p>
     */
    public SnapshotResponse createSnapshot(String sessionId, CreateSnapshotRequest request, CurrentUser user) {
        VideoSession session = requireSession(sessionId);
        if (session.getStatus() != VideoSessionStatus.STREAMING && session.getStatus() != VideoSessionStatus.ROOM_READY) {
            throw new IllegalStateException("Current session is not streaming");
        }
        String snapshotId = "snap_" + compactUuid();
        SnapshotResponse response = new SnapshotResponse(snapshotId, "PROCESSING", "livekit_track", request.getClientPreviewObjectKey() != null, now());
        webSocketPublisher.publish("snapshot.requested", Map.of(
                "snapshotId", snapshotId,
                "sessionId", sessionId,
                "trackSid", request.getTrackSid(),
                "createdBy", user.userId()));
        return response;
    }

    /**
     * 开发联调用 mock：模拟云接入客户端已 ACK。
     */
    @Transactional
    public VideoSessionResponse markClientAcked(String sessionId) {
        VideoSession session = requireSession(sessionId);
        session.setStatus(VideoSessionStatus.CLIENT_ACKED);
        session.setUpdatedAt(now());
        repository.save(session);
        publish("video.client.acked", session);
        return VideoSessionResponse.from(session, properties.getLivekit().getUrl(), null);
    }

    /**
     * 开发联调用 mock：模拟 LiveKit Track 已发布。
     *
     * <p>正式接入后，该状态应由 LiveKit webhook 或云接入客户端 status 上报触发。</p>
     */
    @Transactional
    public VideoSessionResponse markTrackPublished(String sessionId, String trackSid) {
        VideoSession session = requireSession(sessionId);
        session.setTrackSid(trackSid);
        session.setTrackName("video." + session.getChannel() + "." + session.getQuality());
        session.setStatus(VideoSessionStatus.STREAMING);
        session.setStartedAt(now());
        session.setUpdatedAt(now());
        repository.save(session);
        publish("video.track.published", session);
        publish("video.session.streaming", session);
        return VideoSessionResponse.from(session, properties.getLivekit().getUrl(), null);
    }

    public List<VideoSessionResponse> recent(CurrentUser user) {
        return repository.findTop20ByCreatedByOrderByCreatedAtDesc(user.userId()).stream()
                .map(session -> VideoSessionResponse.from(session, properties.getLivekit().getUrl(), null))
                .toList();
    }

    private VideoSession requireSession(String sessionId) {
        return repository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Video session not found: " + sessionId));
    }

    private String roomName(CreateVideoSessionRequest request) {
        return "media." + request.getRobotId() + "." + request.getDeviceId() + "." + request.getChannel();
    }

    private void publish(String event, Object data) {
        webSocketPublisher.publish(event, data);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private String compactUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
