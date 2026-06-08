package com.robot.mediaserver.video.service;

import com.robot.mediaserver.auth.CurrentUser;
import com.robot.mediaserver.config.MediaProperties;
import com.robot.mediaserver.livekit.LiveKitRoomService;
import com.robot.mediaserver.livekit.LiveKitTokenService;
import com.robot.mediaserver.livekit.LiveKitTokenService.TokenResult;
import com.robot.mediaserver.video.dto.CreateSnapshotRequest;
import com.robot.mediaserver.video.dto.CompleteSnapshotRequest;
import com.robot.mediaserver.video.dto.CreateVideoSessionRequest;
import com.robot.mediaserver.video.dto.IntercomResponse;
import com.robot.mediaserver.video.dto.SnapshotResponse;
import com.robot.mediaserver.video.dto.SwitchChannelRequest;
import com.robot.mediaserver.video.dto.VideoSessionResponse;
import com.robot.mediaserver.video.dto.ViewerTokenResponse;
import com.robot.mediaserver.video.event.MediaEventLogService;
import com.robot.mediaserver.video.messaging.VideoStartCommand;
import com.robot.mediaserver.video.messaging.IntercomStartCommand;
import com.robot.mediaserver.video.model.MediaSessionViewer;
import com.robot.mediaserver.video.model.IntercomStatus;
import com.robot.mediaserver.video.model.VideoSession;
import com.robot.mediaserver.video.model.VideoSessionStatus;
import com.robot.mediaserver.video.repository.MediaSessionViewerRepository;
import com.robot.mediaserver.video.repository.VideoSessionRepository;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 实时视频会话编排服务。
 *
 * <p>该服务负责媒体会话落库、LiveKit Room/Token、Track 状态和 viewerCount。
 * MQTT 指令由控制服务根据本服务返回的命令数据统一下发。</p>
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

    /**
     * 实时视频会话仓储。
     */
    private final VideoSessionRepository repository;

    /**
     * 观看者会话仓储。
     */
    private final MediaSessionViewerRepository viewerRepository;

    /**
     * LiveKit 房间服务。
     */
    private final LiveKitRoomService liveKitRoomService;

    /**
     * LiveKit token 服务。
     */
    private final LiveKitTokenService liveKitTokenService;

    /**
     * 媒体事件日志服务。
     */
    private final MediaEventLogService eventLogService;

    /**
     * 抓拍服务。
     */
    private final SnapshotService snapshotService;

    /**
     * 媒体轨道服务。
     */
    private final MediaTrackService mediaTrackService;

    /**
     * 媒体服务配置属性。
     */
    private final MediaProperties properties;

    /**
     * 构造实时视频会话编排服务。
     *
     * @param repository 实时视频会话仓储
     * @param viewerRepository 观看者会话仓储
     * @param liveKitRoomService LiveKit 房间服务
     * @param liveKitTokenService LiveKit token 服务
     * @param eventLogService 媒体事件日志服务
     * @param snapshotService 抓拍服务
     * @param mediaTrackService 媒体轨道服务
     * @param properties 媒体服务配置属性
     */
    public VideoSessionService(
            VideoSessionRepository repository,
            MediaSessionViewerRepository viewerRepository,
            LiveKitRoomService liveKitRoomService,
            LiveKitTokenService liveKitTokenService,
            MediaEventLogService eventLogService,
            SnapshotService snapshotService,
            MediaTrackService mediaTrackService,
            MediaProperties properties) {
        this.repository = repository;
        this.viewerRepository = viewerRepository;
        this.liveKitRoomService = liveKitRoomService;
        this.liveKitTokenService = liveKitTokenService;
        this.eventLogService = eventLogService;
        this.snapshotService = snapshotService;
        this.mediaTrackService = mediaTrackService;
        this.properties = properties;
    }

    /**
     * 创建或复用实时视频会话。
     *
     * <p>主流程：先检查可复用会话；若不存在则创建业务会话，并返回前端观看 Token。
     * 新会话的机器人端 start 指令由 Control Server 调用 requestClientStart 后下发。</p>
     *
     * @param request 创建实时视频会话请求
     * @param user 当前操作用户
     * @return 实时视频会话响应
     */
    @Transactional
    public VideoSessionResponse create(CreateVideoSessionRequest request, CurrentUser user) {
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
            TokenResult viewerToken = createBrowserToken(session, user);
            emit("video.session.reused", session);
            return VideoSessionResponse.from(session, properties.getLivekit().getUrl(), viewerToken.token());
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
        session.setIntercomStatus(IntercomStatus.IDLE);
        session.setOrgId(user.orgId());
        session.setCreatedBy(user.userId());
        session.setCreatedAt(now());
        session.setUpdatedAt(now());
        repository.save(session);
        addViewer(session, user);
        session.setViewerCount(activeViewerCount(session.getSessionId()));
        repository.save(session);
        emit("video.session.created", session);

        TokenResult viewerToken = createBrowserToken(session, user);
        return VideoSessionResponse.from(session, properties.getLivekit().getUrl(), viewerToken.token());
    }

    /**
     * 创建或复用承载对讲的 VideoSession，不计入视频观看人数，也不触发视频发布。
     *
     * @param request 创建实时视频会话请求
     * @param user 当前操作用户
     * @return 对讲启动响应
     */
    @Transactional
    public IntercomResponse createForIntercom(CreateVideoSessionRequest request, CurrentUser user) {
        VideoSession session = repository.findFirstByRobotIdAndDeviceIdAndChannelAndQualityAndStatusInOrderByCreatedAtDesc(
                        request.getRobotId(),
                        request.getDeviceId(),
                        request.getChannel(),
                        request.getQuality(),
                        REUSABLE_STATUSES)
                .orElseGet(() -> {
                    VideoSession created = new VideoSession();
                    created.setSessionId("vs_" + compactUuid());
                    created.setRobotId(request.getRobotId());
                    created.setDeviceId(request.getDeviceId());
                    created.setChannel(request.getChannel());
                    created.setQuality(request.getQuality());
                    created.setRoomName(roomName(request));
                    created.setStatus(VideoSessionStatus.INIT);
                    created.setViewerCount(0);
                    created.setIntercomStatus(IntercomStatus.IDLE);
                    created.setOrgId(user.orgId());
                    created.setCreatedBy(user.userId());
                    created.setCreatedAt(now());
                    created.setUpdatedAt(now());
                    repository.save(created);
                    emit("video.session.created", created);
                    return created;
                });
        return startIntercom(session.getSessionId(), user);
    }

    /**
     * 查询实时视频会话，并生成当前浏览器可用的观看 token。
     *
     * @param sessionId 实时视频会话编号
     * @param user 当前操作用户
     * @return 实时视频会话响应
     */
    public VideoSessionResponse get(String sessionId, CurrentUser user) {
        VideoSession session = requireSession(sessionId);
        TokenResult viewerToken = createBrowserToken(session, user);
        return VideoSessionResponse.from(session, properties.getLivekit().getUrl(), viewerToken.token());
    }

    /**
     * 为浏览器观看端创建 LiveKit viewer token。
     *
     * @param sessionId 实时视频会话编号
     * @param user 当前操作用户
     * @return 观看 token 响应
     */
    public ViewerTokenResponse createViewerToken(String sessionId, CurrentUser user) {
        VideoSession session = requireSession(sessionId);
        TokenResult token = createBrowserToken(session, user);
        return new ViewerTokenResponse(properties.getLivekit().getUrl(), session.getRoomName(), token.token(), token.expiresAt());
    }

    /**
     * 为对讲操作员创建 LiveKit operator token。
     *
     * @param sessionId 实时视频会话编号
     * @param user 当前操作用户
     * @return 对讲 token 响应
     */
    public IntercomResponse createIntercomToken(String sessionId, CurrentUser user) {
        VideoSession session = requireIntercomOperator(sessionId, user);
        TokenResult token = liveKitTokenService.createOperatorToken(session.getRoomName(), user.userId(), user.clientId());
        return IntercomResponse.from(session, properties.getLivekit().getUrl(), token.token(), token.expiresAt());
    }

    /**
     * 刷新观看者心跳，并同步 viewerCount。
     *
     * @param sessionId 实时视频会话编号
     * @param user 当前操作用户
     * @return 实时视频会话响应
     */
    @Transactional
    public VideoSessionResponse heartbeat(String sessionId, CurrentUser user) {
        VideoSession session = requireSession(sessionId);
        addViewer(session, user);
        OffsetDateTime heartbeatAt = now();
        if (holdsRoomForIntercom(session)
                && Objects.equals(session.getIntercomOperatorId(), user.userId())
                && Objects.equals(session.getIntercomClientId(), user.clientId())) {
            session.setIntercomHeartbeatAt(heartbeatAt);
        }
        session.setViewerCount(activeViewerCount(sessionId));
        session.setUpdatedAt(heartbeatAt);
        repository.save(session);
        return VideoSessionResponse.from(session, properties.getLivekit().getUrl(), null);
    }

    /**
     * 当前观看者停止观看实时视频。
     *
     * @param sessionId 实时视频会话编号
     * @param user 当前操作用户
     * @return 实时视频会话响应
     */
    @Transactional
    public VideoSessionResponse stop(String sessionId, CurrentUser user) {
        VideoSession session = requireSession(sessionId);
        removeViewer(sessionId, user);
        session.setViewerCount(activeViewerCount(sessionId));
        if (session.getViewerCount() == 0 && !holdsRoomForIntercom(session)) {
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

    /**
     * 在实时视频会话中启动对讲。
     *
     * @param sessionId 实时视频会话编号
     * @param user 当前操作用户
     * @return 对讲启动响应
     */
    @Transactional
    public IntercomResponse startIntercom(String sessionId, CurrentUser user) {
        VideoSession session = requireSession(sessionId);
        // 对讲同一时间只能由一个浏览器 client 占用。判断 clientId 可以避免同一用户
        // 开多个页面时互相抢占，心跳超时后 expireIntercom 会释放这些字段。
        if (holdsRoomForIntercom(session)
                && (!Objects.equals(session.getIntercomOperatorId(), user.userId())
                || !Objects.equals(session.getIntercomClientId(), user.clientId()))) {
            throw new IllegalStateException("Intercom is occupied by another operator");
        }
        liveKitRoomService.createRoom(session.getRoomName());
        // 如果这个会话原本只是空壳或空闲等待，对讲需要先确保 Room 可用。
        // 但是否发布视频取决于前端是否也发起观看请求。
        if (session.getStatus() == VideoSessionStatus.INIT || session.getStatus() == VideoSessionStatus.IDLE_WAIT) {
            session.setStatus(VideoSessionStatus.ROOM_READY);
        }
        session.setIdleSince(null);
        session.setIntercomStatus(IntercomStatus.STARTING);
        session.setIntercomAudioOnly(session.getTrackSid() == null || session.getTrackSid().isBlank());
        session.setIntercomOperatorId(user.userId());
        session.setIntercomClientId(user.clientId());
        session.setIntercomStartedAt(session.getIntercomStartedAt() == null ? now() : session.getIntercomStartedAt());
        session.setIntercomHeartbeatAt(now());
        session.setUpdatedAt(now());
        repository.save(session);
        emit("video.intercom.starting", session);
        TokenResult token = liveKitTokenService.createOperatorToken(session.getRoomName(), user.userId(), user.clientId());
        return IntercomResponse.from(session, properties.getLivekit().getUrl(), token.token(), token.expiresAt());
    }

    /**
     * 刷新对讲操作员心跳。
     *
     * @param sessionId 实时视频会话编号
     * @param user 当前操作用户
     * @return 对讲状态响应
     */
    @Transactional
    public IntercomResponse heartbeatIntercom(String sessionId, CurrentUser user) {
        VideoSession session = requireIntercomOperator(sessionId, user);
        session.setIntercomHeartbeatAt(now());
        session.setUpdatedAt(now());
        repository.save(session);
        TokenResult token = liveKitTokenService.createOperatorToken(session.getRoomName(), user.userId(), user.clientId());
        return IntercomResponse.from(session, properties.getLivekit().getUrl(), token.token(), token.expiresAt());
    }

    /**
     * 停止实时视频会话中的对讲。
     *
     * @param sessionId 实时视频会话编号
     * @param user 当前操作用户
     * @return 实时视频会话响应
     */
    @Transactional
    public VideoSessionResponse stopIntercom(String sessionId, CurrentUser user) {
        VideoSession session = requireIntercomOperator(sessionId, user);
        session.setIntercomStatus(IntercomStatus.STOPPING);
        emit("video.intercom.stopping", session);
        // 先发布 stopping 事件，再清空占用信息。这样前端能看到明确的“正在挂断”
        // 过渡，同时后续 startIntercom 不会被旧 operator 锁住。
        session.setIntercomStatus(IntercomStatus.IDLE);
        session.setIntercomAudioOnly(false);
        session.setIntercomOperatorId(null);
        session.setIntercomClientId(null);
        session.setRobotAudioTrackSid(null);
        session.setRobotAudioTrackName(null);
        session.setIntercomHeartbeatAt(null);
        if (session.getViewerCount() == 0) {
            session.setIdleSince(now());
            transition(session, VideoSessionStatus.IDLE_WAIT, "video.session.idle_wait", Map.of(
                    "sessionId", session.getSessionId(),
                    "idleReleaseDelaySeconds", properties.getSession().getIdleReleaseDelaySeconds()));
        }
        session.setUpdatedAt(now());
        repository.save(session);
        emit("video.intercom.closed", session);
        return VideoSessionResponse.from(session, properties.getLivekit().getUrl(), null);
    }

    /**
     * 创建下发给机器人客户端的对讲启动命令。
     *
     * @param sessionId 实时视频会话编号
     * @return 对讲启动命令
     */
    public IntercomStartCommand createIntercomStartCommand(String sessionId) {
        VideoSession session = requireSession(sessionId);
        // 机器人端只拿到 intercom 专用 token，用来发布机器人麦克风并订阅操作员麦克风。
        // 浏览器 operator token 不会下发到机器人。
        TokenResult robotToken = liveKitTokenService.createRobotIntercomToken(
                session.getRoomName(), session.getRobotId(), session.getDeviceId());
        return new IntercomStartCommand(
                "cmd_" + compactUuid(),
                session.getSessionId(),
                session.getRobotId(),
                session.getDeviceId(),
                session.getRoomName(),
                properties.getLivekit().getUrl(),
                robotToken.token(),
                true,
                true,
                false,
                robotToken.expiresAt());
    }

    /**
     * 切换实时视频通道或码流质量。
     *
     * @param sessionId 实时视频会话编号
     * @param request 通道切换请求
     * @return 实时视频会话响应
     */
    @Transactional
    public VideoSessionResponse switchChannel(String sessionId, SwitchChannelRequest request) {
        VideoSession session = requireSession(sessionId);
        // 通道切换本质上是让同一个业务会话指向新的 RTSP/track。
        // requestClientStart 会更新 commandId 并把状态切到 REQUESTING_CLIENT。
        session.setChannel(request.getChannel());
        if (request.getQuality() != null) {
            session.setQuality(request.getQuality());
        }
        session.setRoomName("media." + session.getRobotId() + "." + session.getDeviceId() + "." + session.getChannel());
        requestClientStart(session, "video.track.switching", false);
        session.setUpdatedAt(now());
        repository.save(session);
        return VideoSessionResponse.from(session, properties.getLivekit().getUrl(), null);
    }

    /**
     * 创建实时视频抓拍任务。
     *
     * @param sessionId 实时视频会话编号
     * @param request 抓拍请求
     * @param user 当前操作用户
     * @return 抓拍响应
     */
    public SnapshotResponse createSnapshot(String sessionId, CreateSnapshotRequest request, CurrentUser user) {
        VideoSession session = requireSession(sessionId);
        if (session.getStatus() != VideoSessionStatus.STREAMING && session.getStatus() != VideoSessionStatus.ROOM_READY) {
            throw new IllegalStateException("Current session is not streaming");
        }
        return snapshotService.create(session, request, user);
    }

    public SnapshotResponse createSnapshotFile(
            String sessionId,
            CreateSnapshotRequest request,
            MultipartFile file,
            CurrentUser user) {
        SnapshotResponse snapshot = createSnapshot(sessionId, request, user);
        CompleteSnapshotRequest completeRequest = new CompleteSnapshotRequest();
        completeRequest.setOfficialCapturedAt(request.getClientCapturedAt());
        return snapshotService.complete(snapshot.snapshotId(), completeRequest, file);
    }

    /**
     * 标记 LiveKit 视频轨道已经发布。
     *
     * @param sessionId 实时视频会话编号
     * @param trackSid LiveKit track sid
     * @return 实时视频会话响应
     */
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


    /**
     * 处理机器人客户端上报的视频推流状态。
     *
     * @param sessionId 实时视频会话编号
     * @param status 机器人客户端状态
     * @param trackSid LiveKit track sid
     * @param trackName LiveKit track 名称
     * @param errorCode 错误码
     * @param message 状态说明
     */
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
        // Go 客户端通过 MQTT 回报的是轻量字符串状态，这里统一映射为后端状态机枚举，
        // 并在关键节点发布 WebSocket 事件，驱动前端刷新。
        switch (normalized) {
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

    /**
     * 处理机器人客户端上报的对讲状态。
     *
     * @param sessionId 实时视频会话编号
     * @param status 对讲状态
     * @param robotAudioTrackSid 机器人麦克风 track sid
     * @param robotAudioTrackName 机器人麦克风 track 名称
     * @param errorCode 错误码
     * @param message 状态说明
     */
    @Transactional
    public void handleIntercomStatus(
            String sessionId,
            String status,
            String robotAudioTrackSid,
            String robotAudioTrackName,
            String errorCode,
            String message) {
        VideoSession session = requireSession(sessionId);
        String normalized = status == null ? "" : status.trim().toLowerCase();
        // 对讲状态独立于视频状态：视频可能仍在 STREAMING，而对讲已经 IDLE/FAILED。
        // 因此这里只更新 intercom 字段，必要时才把无人观看的会话切到 IDLE_WAIT。
        switch (normalized) {
            case "starting" -> session.setIntercomStatus(IntercomStatus.STARTING);
            case "active" -> {
                session.setIntercomStatus(IntercomStatus.ACTIVE);
                session.setRobotAudioTrackSid(robotAudioTrackSid);
                session.setRobotAudioTrackName(robotAudioTrackName == null || robotAudioTrackName.isBlank()
                        ? "audio.robot.mic" : robotAudioTrackName);
                emit("video.intercom.active", session);
            }
            case "interrupted" -> {
                session.setIntercomStatus(IntercomStatus.INTERRUPTED);
                eventLogService.recordAndPublish(sessionId, "video.intercom.interrupted", Map.of(
                        "sessionId", sessionId, "message", safeMessage(message)));
            }
            case "stopped", "closed" -> {
                session.setIntercomStatus(IntercomStatus.IDLE);
                session.setRobotAudioTrackSid(null);
                session.setRobotAudioTrackName(null);
                emit("video.intercom.closed", session);
            }
            case "failed", "error" -> {
                session.setIntercomStatus(IntercomStatus.FAILED);
                eventLogService.recordAndPublish(sessionId, "video.intercom.failed", Map.of(
                        "sessionId", sessionId,
                        "errorCode", errorCode == null ? "INTERCOM_FAILED" : errorCode,
                        "message", safeMessage(message)));
            }
            default -> eventLogService.recordAndPublish(sessionId, "video.intercom.status", Map.of(
                    "sessionId", sessionId, "status", safeMessage(status), "message", safeMessage(message)));
        }
        if (!holdsRoomForIntercom(session)
                && session.getViewerCount() == 0
                && session.getStatus() != VideoSessionStatus.IDLE_WAIT) {
            session.setIdleSince(now());
            transition(session, VideoSessionStatus.IDLE_WAIT, "video.session.idle_wait", Map.of(
                    "sessionId", session.getSessionId(),
                    "idleReleaseDelaySeconds", properties.getSession().getIdleReleaseDelaySeconds()));
        }
        session.setUpdatedAt(now());
        repository.save(session);
    }

    /**
     * 机器人客户端上线后，生成需要自动恢复的视频启动命令。
     *
     * @param robotId 机器人编号
     * @param status 机器人客户端状态
     * @return 待下发的视频启动命令列表
     */
    @Transactional
    public List<VideoStartCommand> handleClientOnline(String robotId, String status) {
        if (!"online".equalsIgnoreCase(status)) {
            return List.of();
        }
        // 机器人重连后只对每个 robot/device/channel/quality 组合重启最新一个会话，
        // 避免历史失败会话批量下发造成同一路摄像头重复推流。
        Set<String> restartedKeys = new HashSet<>();
        return repository.findByRobotIdAndViewerCountGreaterThanAndStatusInOrderByUpdatedAtDesc(
                        robotId,
                        0,
                        Set.of(
                                VideoSessionStatus.REQUESTING_CLIENT,
                                VideoSessionStatus.ROOM_READY,
                                VideoSessionStatus.STREAMING,
                                VideoSessionStatus.INTERRUPTED,
                                VideoSessionStatus.FAILED,
                                VideoSessionStatus.TIMEOUT))
                .stream()
                .map(session -> {
                    String key = session.getRobotId() + ":" + session.getDeviceId() + ":" + session.getChannel() + ":" + session.getQuality();
                    if (restartedKeys.add(key)) {
                        return requestClientStart(session, "video.client.online_restart", false);
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 当前用户手动重启实时视频会话。
     *
     * @param sessionId 实时视频会话编号
     * @param user 当前操作用户
     * @return 实时视频会话响应
     */
    @Transactional
    public VideoSessionResponse restartSession(String sessionId, CurrentUser user) {
        VideoSession session = requireSession(sessionId);
        addViewer(session, user);
        session.setViewerCount(activeViewerCount(sessionId));
        requestClientStart(session, "video.session.restart", false);
        return VideoSessionResponse.from(session, properties.getLivekit().getUrl(), null);
    }

    /**
     * 后台任务自动重启实时视频会话。
     *
     * @param sessionId 实时视频会话编号
     */
    @Transactional
    public void restartSession(String sessionId) {
        requestClientStart(requireSession(sessionId), "video.session.auto_restart", false);
    }

    /**
     * 当前用户手动重启实时视频会话，并返回待下发的视频启动命令。
     *
     * @param sessionId 实时视频会话编号
     * @param user 当前操作用户
     * @return 视频启动命令
     */
    @Transactional
    public VideoStartCommand restartSessionCommand(String sessionId, CurrentUser user) {
        VideoSession session = requireSession(sessionId);
        addViewer(session, user);
        session.setViewerCount(activeViewerCount(sessionId));
        return requestClientStart(session, "video.session.restart", false);
    }

    /**
     * 后台任务自动重启实时视频会话，并返回待下发的视频启动命令。
     *
     * @param sessionId 实时视频会话编号
     * @return 视频启动命令
     */
    @Transactional
    public VideoStartCommand restartSessionCommand(String sessionId) {
        return requestClientStart(requireSession(sessionId), "video.session.auto_restart", false);
    }

    /**
     * 为会话请求机器人客户端开始推流。
     *
     * @param sessionId 实时视频会话编号
     * @param event 事件名称
     * @return 视频启动命令
     */
    @Transactional
    public VideoStartCommand requestClientStart(String sessionId, String event) {
        return requestClientStart(requireSession(sessionId), event, true);
    }

    /**
     * 根据当前会话状态创建视频启动命令。
     *
     * @param sessionId 实时视频会话编号
     * @return 视频启动命令
     */
    @Transactional
    public VideoStartCommand createStartCommand(String sessionId) {
        return createStartCommand(requireSession(sessionId));
    }

    private VideoStartCommand requestClientStart(VideoSession session, String event, boolean includeTimeout) {
        if (session.getViewerCount() <= 0) {
            return null;
        }
        // 生成机器人推流命令前先确保 LiveKit Room 存在，再签发 publisher token。
        // 命令发送本身不在本服务做，调用方可决定通过 MQTT 或其他控制通道下发。
        liveKitRoomService.createRoom(session.getRoomName());
        if (holdsRoomForIntercom(session)) {
            session.setIntercomAudioOnly(false);
        }
        emit("video.room.ready", Map.of(
                "sessionId", session.getSessionId(),
                "roomName", session.getRoomName()));
        TokenResult publisherToken = liveKitTokenService.createPublisherToken(
                session.getRoomName(), session.getRobotId(), session.getDeviceId());
        String commandId = "cmd_" + compactUuid();
        session.setCommandId(commandId);
        session.setCommandRequestedAt(now());
        session.setEndedAt(null);
        session.setLastErrorCode(null);
        session.setLastErrorMessage(null);
        Map<String, Object> payload = includeTimeout
                ? Map.of(
                        "sessionId", session.getSessionId(),
                        "commandId", commandId,
                        "timeoutSeconds", properties.getSession().getTrackPublishTimeoutSeconds())
                : Map.of(
                        "sessionId", session.getSessionId(),
                        "commandId", commandId);
        transition(session, VideoSessionStatus.REQUESTING_CLIENT, event, payload);
        repository.save(session);
        return new VideoStartCommand(
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
                publisherToken.expiresAt());
    }

    private VideoStartCommand createStartCommand(VideoSession session) {
        TokenResult publisherToken = liveKitTokenService.createPublisherToken(
                session.getRoomName(), session.getRobotId(), session.getDeviceId());
        return new VideoStartCommand(
                session.getCommandId(),
                session.getSessionId(),
                session.getRobotId(),
                session.getDeviceId(),
                session.getChannel(),
                session.getQuality(),
                properties.getLivekit().getUrl(),
                session.getRoomName(),
                publisherToken.token(),
                "robot:" + session.getRobotId() + ":" + session.getDeviceId(),
                publisherToken.expiresAt());
    }

    /**
     * 标记实时视频会话推流超时。
     *
     * @param sessionId 实时视频会话编号
     * @param errorCode 错误码
     * @param message 错误说明
     */
    @Transactional
    public void markTimeout(String sessionId, String errorCode, String message) {
        VideoSession session = requireSession(sessionId);
        session.setEndedAt(now());
        markFailed(session, errorCode, message, "video.session.failed");
        session.setUpdatedAt(now());
        repository.save(session);
    }

    /**
     * 释放已经进入空闲等待状态的实时视频会话。
     *
     * @param sessionId 实时视频会话编号
     * @return 需要下发给机器人客户端的停止命令载荷；不需要释放时返回空 Map
     */
    @Transactional
    public Map<String, Object> releaseIdleSession(String sessionId) {
        VideoSession session = requireSession(sessionId);
        if (session.getStatus() != VideoSessionStatus.IDLE_WAIT
                || session.getViewerCount() > 0
                || holdsRoomForIntercom(session)) {
            return Map.of();
        }
        // 空闲释放是两阶段的：先把会话状态关掉并删除 LiveKit Room，
        // 再把 stop payload 返回给控制服务，由控制服务通知机器人停止本地推流进程。
        transition(session, VideoSessionStatus.STOPPING, "video.session.stopping", session);
        mediaTrackService.unpublish(session);
        Map<String, Object> stopPayload = Map.of(
                "robotId", session.getRobotId(),
                "sessionId", session.getSessionId(),
                "commandId", "cmd_" + compactUuid(),
                "roomName", session.getRoomName());
        liveKitRoomService.deleteRoom(session.getRoomName());
        session.setEndedAt(now());
        transition(session, VideoSessionStatus.CLOSED, "video.session.closed", session);
        repository.save(session);
        return stopPayload;
    }

    /**
     * 查询当前用户最近创建的实时视频会话。
     *
     * @param user 当前操作用户
     * @return 最近实时视频会话列表
     */
    public List<VideoSessionResponse> recent(CurrentUser user) {
        return repository.findTop20ByCreatedByOrderByCreatedAtDesc(user.userId()).stream()
                .map(session -> VideoSessionResponse.from(session, properties.getLivekit().getUrl(), null))
                .toList();
    }

    /**
     * 查询当前可复用的实时视频会话。
     *
     * @return 活跃实时视频会话列表
     */
    public List<VideoSessionResponse> active() {
        return repository.findTop16ByStatusInOrderByUpdatedAtDesc(REUSABLE_STATUSES).stream()
                .map(session -> VideoSessionResponse.from(session, properties.getLivekit().getUrl(), null))
                .toList();
    }

    /**
     * 查询需要自动重启的中断会话。
     *
     * @param updatedBefore 更新时间阈值
     * @return 会话编号列表
     */
    public List<String> interruptedRestartCandidates(OffsetDateTime updatedBefore) {
        return repository.findByStatusAndUpdatedAtBefore(VideoSessionStatus.INTERRUPTED, updatedBefore).stream()
                .filter(session -> session.getViewerCount() > 0)
                .map(VideoSession::getSessionId)
                .toList();
    }

    /**
     * 查询需要释放的空闲会话。
     *
     * @param idleSinceBefore 空闲开始时间阈值
     * @return 会话编号列表
     */
    public List<String> idleReleaseCandidates(OffsetDateTime idleSinceBefore) {
        return repository.findByStatusAndIdleSinceBefore(VideoSessionStatus.IDLE_WAIT, idleSinceBefore).stream()
                .filter(session -> session.getViewerCount() == 0 && !holdsRoomForIntercom(session))
                .map(VideoSession::getSessionId)
                .toList();
    }

    /**
     * 查询对讲心跳超时的会话。
     *
     * @param heartbeatBefore 心跳时间阈值
     * @return 会话编号列表
     */
    public List<String> intercomTimeoutCandidates(OffsetDateTime heartbeatBefore) {
        return repository.findByIntercomStatusInAndIntercomHeartbeatAtBefore(
                        Set.of(IntercomStatus.STARTING, IntercomStatus.ACTIVE),
                        heartbeatBefore).stream()
                .map(VideoSession::getSessionId)
                .toList();
    }

    /**
     * 使对讲会话超时失效，并返回机器人端停止对讲命令载荷。
     *
     * @param sessionId 实时视频会话编号
     * @return 需要下发给机器人客户端的对讲停止命令载荷；不需要停止时返回空 Map
     */
    @Transactional
    public Map<String, Object> expireIntercom(String sessionId) {
        VideoSession session = requireSession(sessionId);
        if (!holdsRoomForIntercom(session)) {
            return Map.of();
        }
        session.setIntercomStatus(IntercomStatus.INTERRUPTED);
        session.setIntercomAudioOnly(false);
        session.setIntercomOperatorId(null);
        session.setIntercomClientId(null);
        session.setRobotAudioTrackSid(null);
        session.setRobotAudioTrackName(null);
        eventLogService.recordAndPublish(sessionId, "video.intercom.interrupted", Map.of(
                "sessionId", sessionId,
                "message", "intercom heartbeat timeout"));
        if (session.getViewerCount() == 0) {
            session.setIdleSince(now());
            transition(session, VideoSessionStatus.IDLE_WAIT, "video.session.idle_wait", Map.of(
                    "sessionId", sessionId,
                    "idleReleaseDelaySeconds", properties.getSession().getIdleReleaseDelaySeconds()));
        }
        session.setUpdatedAt(now());
        repository.save(session);
        return Map.of(
                "robotId", session.getRobotId(),
                "sessionId", sessionId,
                "commandId", "cmd_" + compactUuid(),
                "roomName", session.getRoomName());
    }

    /**
     * 清理心跳过期的观看者。
     */
    @Transactional
    public void sweepStaleViewers() {
        OffsetDateTime threshold = now().minusSeconds(properties.getSession().getViewerHeartbeatTimeoutSeconds());
        viewerRepository.findByLeftAtIsNullAndLastHeartbeatAtBefore(threshold).forEach(viewer -> {
            closeViewer(viewer);
        });
    }

    /**
     * 关闭全部仍处于活跃状态的观看者。
     */
    @Transactional
    public void closeAllActiveViewers() {
        viewerRepository.findByLeftAtIsNull().forEach(this::closeViewer);
    }

    private void closeViewer(MediaSessionViewer viewer) {
        viewer.setLeftAt(now());
        viewerRepository.save(viewer);
        repository.findById(viewer.getSessionId()).ifPresent(session -> {
            session.setViewerCount(activeViewerCount(session.getSessionId()));
            // 最后一个 viewer 离开后不立刻停止机器人推流，而是进入 IDLE_WAIT。
            // 这样短时间内重新打开页面可以复用 Room/Track，减少推流启动延迟。
            if (session.getViewerCount() == 0
                    && session.getStatus() == VideoSessionStatus.STREAMING
                    && !holdsRoomForIntercom(session)) {
                session.setIdleSince(now());
                transition(session, VideoSessionStatus.IDLE_WAIT, "video.session.idle_wait", Map.of(
                        "sessionId", session.getSessionId(),
                        "idleReleaseDelaySeconds", properties.getSession().getIdleReleaseDelaySeconds()));
            } else {
                emit("video.viewer.changed", session);
            }
            session.setUpdatedAt(now());
            repository.save(session);
        });
    }

    private VideoSession requireSession(String sessionId) {
        return repository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Video session not found: " + sessionId));
    }

    private VideoSession requireIntercomOperator(String sessionId, CurrentUser user) {
        VideoSession session = requireSession(sessionId);
        if (!Objects.equals(session.getIntercomOperatorId(), user.userId())
                || !Objects.equals(session.getIntercomClientId(), user.clientId())) {
            throw new IllegalStateException("Current user does not own the intercom");
        }
        return session;
    }

    private boolean holdsRoomForIntercom(VideoSession session) {
        return session.getIntercomStatus() == IntercomStatus.STARTING
                || session.getIntercomStatus() == IntercomStatus.ACTIVE;
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
        String identity = viewerIdentity(user);
        // 同一浏览器 client 重复心跳时只刷新 lastHeartbeatAt；新的标签页会得到不同 clientId，
        // 因而会被计为独立 viewer。
        viewerRepository.findFirstBySessionIdAndParticipantIdentityAndLeftAtIsNull(session.getSessionId(), identity)
                .map(viewer -> {
                    viewer.setLastHeartbeatAt(now());
                    return viewerRepository.save(viewer);
                })
                .orElseGet(() -> {
                    MediaSessionViewer viewer = new MediaSessionViewer();
                    viewer.setId("viewer_" + compactUuid());
                    viewer.setSessionId(session.getSessionId());
                    viewer.setUserId(user.userId());
                    viewer.setOrgId(user.orgId());
                    viewer.setParticipantIdentity(identity);
                    viewer.setClientType("web");
                    viewer.setJoinedAt(now());
                    viewer.setLastHeartbeatAt(now());
                    return viewerRepository.save(viewer);
                });
    }

    private void removeViewer(String sessionId, CurrentUser user) {
        viewerRepository.findFirstBySessionIdAndParticipantIdentityAndLeftAtIsNull(sessionId, viewerIdentity(user))
                .ifPresent(viewer -> {
                    viewer.setLeftAt(now());
                    viewerRepository.save(viewer);
                });
    }

    private int activeViewerCount(String sessionId) {
        return Math.toIntExact(viewerRepository.countBySessionIdAndLeftAtIsNull(sessionId));
    }

    private String viewerIdentity(CurrentUser user) {
        return "user:" + user.userId() + ":" + user.clientId();
    }

    private TokenResult createBrowserToken(VideoSession session, CurrentUser user) {
        // 操作员 token 允许发布麦克风，用于对讲；普通 viewer token 只允许订阅媒体。
        if (user.hasRole("MEDIA_OPERATOR")) {
            return liveKitTokenService.createInteractiveViewerToken(
                    session.getRoomName(), user.userId(), user.clientId());
        }
        return liveKitTokenService.createViewerToken(session.getRoomName(), user.userId(), user.clientId());
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private String compactUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
