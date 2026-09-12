package com.robot.mediaserver.video.service;

import com.robot.mediaserver.auth.CurrentUser;
import com.robot.mediaserver.config.MediaProperties;
import com.robot.mediaserver.livekit.LiveKitRoomService;
import com.robot.mediaserver.livekit.LiveKitRoomService.ActiveVideoTrack;
import com.robot.mediaserver.livekit.LiveKitTokenService;
import com.robot.mediaserver.livekit.LiveKitTokenService.TokenResult;
import com.robot.mediaserver.video.dto.VideoSessionResponses;

import com.robot.media.common.file.FileListItemResponse;
import com.robot.mediaserver.file.service.FileService;
import com.robot.media.common.video.CreateVideoSessionRequest;
import com.robot.media.common.video.IntercomResponse;
import com.robot.media.common.video.SwitchChannelRequest;
import com.robot.media.common.video.VideoChannel;
import com.robot.media.common.video.VideoQuality;
import com.robot.media.common.video.VideoSessionResponse;
import com.robot.media.common.video.ViewerTokenResponse;
import com.robot.media.common.video.VideoStartCommand;
import com.robot.media.common.video.IntercomStartCommand;
import com.robot.mediaserver.video.model.MediaSessionViewer;
import com.robot.mediaserver.video.model.VideoSourceRuntime;
import com.robot.media.common.video.IntercomStatus;
import com.robot.mediaserver.video.model.VideoSession;
import com.robot.media.common.video.VideoSessionStatus;
import com.robot.media.common.video.VideoSourceType;
import com.robot.mediaserver.video.repository.MediaSessionViewerRepository;
import com.robot.mediaserver.video.repository.VideoSessionRepository;
import com.robot.mediaserver.video.repository.VideoSourceRuntimeRepository;
import com.robot.mediaserver.ws.MediaWebSocketPublisher;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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

    private static final Logger log = LoggerFactory.getLogger(VideoSessionService.class);
    private static final int LAST_ERROR_CODE_MAX_LENGTH = 64;
    private static final int LAST_ERROR_MESSAGE_MAX_LENGTH = 512;

    /**
     * 可复用状态集合。
     *
     * <p>同一 robotId + deviceId + channel + quality 已经存在可复用会话时，
     * 新观看者只增加 viewerCount 并复用原 Room/Track，避免重复下发 start 指令。</p>
     */
    private static final Set<VideoSessionStatus> REUSABLE_STATUSES = Set.of(
            VideoSessionStatus.INIT,
            VideoSessionStatus.REQUESTING_CLIENT,
            VideoSessionStatus.ROOM_READY,
            VideoSessionStatus.STREAMING,
            VideoSessionStatus.INTERRUPTED,
            VideoSessionStatus.IDLE_WAIT);

    private static final Set<VideoSessionStatus> RECONCILE_STATUSES = Set.of(
            VideoSessionStatus.REQUESTING_CLIENT,
            VideoSessionStatus.ROOM_READY,
            VideoSessionStatus.STREAMING,
            VideoSessionStatus.INTERRUPTED,
            VideoSessionStatus.IDLE_WAIT);

    private static final Set<VideoSessionStatus> UNOCCUPIED_RECONCILE_STATUSES = Set.of(
            VideoSessionStatus.INIT,
            VideoSessionStatus.REQUESTING_CLIENT,
            VideoSessionStatus.ROOM_READY,
            VideoSessionStatus.STREAMING,
            VideoSessionStatus.INTERRUPTED,
            VideoSessionStatus.FAILED,
            VideoSessionStatus.TIMEOUT);

    /**
     * 实时视频会话仓储。
     */
    private final VideoSessionRepository repository;

    private final VideoSourceRuntimeRepository sourceRuntimeRepository;

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
     * 媒体状态 WebSocket 发布器。
     */
    private final MediaWebSocketPublisher webSocketPublisher;

    private final FileService fileService;

    /**
     * 媒体轨道服务。
     */
    private final MediaTrackService mediaTrackService;

    /**
     * 媒体服务配置属性。
     */
    private final MediaProperties properties;

    private final TransactionTemplate transactionTemplate;

    private final EntityManager entityManager;

    /**
     * 构造实时视频会话编排服务。
     *
     * @param repository 实时视频会话仓储
     * @param viewerRepository 观看者会话仓储
     * @param liveKitRoomService LiveKit 房间服务
     * @param liveKitTokenService LiveKit token 服务
     * @param webSocketPublisher 媒体状态 WebSocket 发布器
     * @param mediaTrackService 媒体轨道服务
     * @param properties 媒体服务配置属性
     */
    public VideoSessionService(
            VideoSessionRepository repository,
            VideoSourceRuntimeRepository sourceRuntimeRepository,
            MediaSessionViewerRepository viewerRepository,
            LiveKitRoomService liveKitRoomService,
            LiveKitTokenService liveKitTokenService,
            MediaWebSocketPublisher webSocketPublisher,
            FileService fileService,
            MediaTrackService mediaTrackService,
            MediaProperties properties,
            PlatformTransactionManager transactionManager,
            EntityManager entityManager) {
        this.repository = repository;
        this.sourceRuntimeRepository = sourceRuntimeRepository;
        this.viewerRepository = viewerRepository;
        this.liveKitRoomService = liveKitRoomService;
        this.liveKitTokenService = liveKitTokenService;
        this.webSocketPublisher = webSocketPublisher;
        this.fileService = fileService;
        this.mediaTrackService = mediaTrackService;
        this.properties = properties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setIsolationLevelName("ISOLATION_READ_COMMITTED");
        this.entityManager = entityManager;
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
        VideoSourceRuntime runtime = lockSourceRuntime(
                request.getSourceType(),
                request.getSourceId(),
                request.getDeviceId(),
                request.getChannel(),
                request.getQuality(),
                roomName(request));
        if (request.isReuse()) {
            var existing = repository.findFirstByRuntimeIdAndStatusInOrderByCreatedAtDesc(
                    runtime.getRuntimeId(), REUSABLE_STATUSES);
            if (existing.isEmpty()) {
                existing = repository.findFirstBySourceTypeAndSourceIdAndDeviceIdAndChannelAndQualityAndStatusInOrderByCreatedAtDesc(
                        request.getSourceType(),
                        request.getSourceId(),
                        request.getDeviceId(),
                        request.getChannel(),
                        request.getQuality(),
                        REUSABLE_STATUSES);
            }
            if (existing.isPresent()) {
                VideoSession session = existing.get();
                session.setRuntimeId(runtime.getRuntimeId());
                session.setRoomName(runtime.getRoomName());
                addViewer(session, user);
                session.setIdleSince(null);
                if (session.getStatus() == VideoSessionStatus.INTERRUPTED
                        || (!hasPublishedTrack(session) && !startRequestInFlight(session))) {
                    session.setStatus(VideoSessionStatus.INIT);
                    session.setTrackSid(null);
                    session.setTrackName(null);
                } else if (session.getStatus() == VideoSessionStatus.IDLE_WAIT) {
                    session.setStatus(VideoSessionStatus.STREAMING);
                }
                session.setViewerCount(activeViewerCount(session.getSessionId()));
                session.setUpdatedAt(now());
                repository.save(session);
                TokenResult viewerToken = createBrowserToken(session, user);
                emit("video.session.reused", session);
                return VideoSessionResponses.from(session, properties.getLivekit().getUrl(), viewerToken.token());
            }
        }

        VideoSession session = new VideoSession();
        session.setSessionId("vs_" + compactUuid());
        session.setRobotId(request.getRobotId());
        session.setSourceType(request.getSourceType());
        session.setSourceId(request.getSourceId());
        session.setRuntimeId(runtime.getRuntimeId());
        session.setDeviceId(request.getDeviceId());
        session.setChannel(request.getChannel());
        session.setQuality(request.getQuality());
        session.setRoomName(runtime.getRoomName());
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
        return VideoSessionResponses.from(session, properties.getLivekit().getUrl(), viewerToken.token());
    }

    private boolean hasPublishedTrack(VideoSession session) {
        if (session.getTrackSid() == null
                || session.getTrackSid().isBlank()
                || session.getTrackName() == null
                || session.getTrackName().isBlank()) {
            return false;
        }
        try {
            return liveKitRoomService.resolveActiveVideoTrack(
                            session.getRoomName(), publisherIdentity(session), session.getTrackSid())
                    .map(track -> {
                        session.setTrackSid(track.trackSid());
                        if (track.trackName() != null) {
                            session.setTrackName(track.trackName());
                        }
                        return true;
                    })
                    .orElse(false);
        } catch (RuntimeException exception) {
            // Room API 暂时不可用时保留已有会话，避免查询故障反向触发推流重启。
            log.warn("校验 LiveKit 视频轨道失败，暂时保留会话 sessionId={}", session.getSessionId(), exception);
            return true;
        }
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
        VideoSourceRuntime runtime = lockSourceRuntime(
                request.getSourceType(),
                request.getSourceId(),
                request.getDeviceId(),
                request.getChannel(),
                request.getQuality(),
                roomName(request));
        Optional<VideoSession> existing = repository.findFirstByRuntimeIdAndStatusInOrderByCreatedAtDesc(
                runtime.getRuntimeId(), REUSABLE_STATUSES);
        if (existing.isEmpty()) {
            existing = repository.findFirstBySourceTypeAndSourceIdAndDeviceIdAndChannelAndQualityAndStatusInOrderByCreatedAtDesc(
                    request.getSourceType(),
                    request.getSourceId(),
                    request.getDeviceId(),
                    request.getChannel(),
                    request.getQuality(),
                    REUSABLE_STATUSES);
        }
        VideoSession session = existing
                .map(value -> {
                    value.setRuntimeId(runtime.getRuntimeId());
                    value.setRoomName(runtime.getRoomName());
                    return value;
                })
                .orElseGet(() -> {
                    VideoSession created = new VideoSession();
                    created.setSessionId("vs_" + compactUuid());
                    created.setRobotId(request.getRobotId());
                    created.setSourceType(request.getSourceType());
                    created.setSourceId(request.getSourceId());
                    created.setRuntimeId(runtime.getRuntimeId());
                    created.setDeviceId(request.getDeviceId());
                    created.setChannel(request.getChannel());
                    created.setQuality(request.getQuality());
                    created.setRoomName(runtime.getRoomName());
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
        return VideoSessionResponses.from(session, properties.getLivekit().getUrl(), viewerToken.token());
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
        return VideoSessionResponses.intercom(session, properties.getLivekit().getUrl(), token.token(), token.expiresAt());
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
        VideoSession session = requireOpenSession(lockSessionRuntime(sessionId));
        addViewer(session, user);
        OffsetDateTime heartbeatAt = now();
        boolean wasIdle = session.getStatus() == VideoSessionStatus.IDLE_WAIT;
        boolean resumeStreaming = wasIdle && hasPublishedTrack(session);
        if (holdsRoomForIntercom(session)
                && Objects.equals(session.getIntercomOperatorId(), user.userId())
                && Objects.equals(session.getIntercomClientId(), user.clientId())) {
            session.setIntercomHeartbeatAt(heartbeatAt);
        }
        session.setViewerCount(activeViewerCount(sessionId));
        if (resumeStreaming) {
            session.setStatus(VideoSessionStatus.STREAMING);
            session.setIdleSince(null);
        } else if (wasIdle) {
            session.setStatus(VideoSessionStatus.INTERRUPTED);
            session.setLastStatusAt(heartbeatAt);
            session.setIdleSince(null);
        }
        session.setUpdatedAt(heartbeatAt);
        repository.save(session);
        if (resumeStreaming) {
            emit("video.session.streaming", session);
        } else if (wasIdle) {
            emit("video.session.interrupted", Map.of(
                    "sessionId", sessionId,
                    "message", "Viewer resumed while LiveKit Publisher/Track missing"));
        }
        return VideoSessionResponses.from(session, properties.getLivekit().getUrl(), null);
    }

    /**
     * 当前观看者停止观看实时视频。
     *
     * @param sessionId 实时视频会话编号
     * @param user 当前操作用户
     * @return 实时视频会话响应
     */
    public VideoSessionResponse stop(String sessionId, CurrentUser user) {
        stopClientRecordingQuietly(sessionId, user.userId(), user.clientId());
        return transactionTemplate.execute(status -> stopViewer(sessionId, user));
    }

    private VideoSessionResponse stopViewer(String sessionId, CurrentUser user) {
        VideoSession session = lockSessionRuntime(sessionId);
        if (session.getStatus() == VideoSessionStatus.CLOSED) {
            return VideoSessionResponses.from(session, properties.getLivekit().getUrl(), null);
        }
        removeViewer(sessionId, user);
        session.setViewerCount(activeViewerCount(sessionId));
        if (!enterIdleWhenUnoccupied(session)) {
            emit("video.viewer.changed", session);
        }
        session.setUpdatedAt(now());
        repository.save(session);
        return VideoSessionResponses.from(session, properties.getLivekit().getUrl(), null);
    }

    /**
     * 在实时视频会话中启动对讲。
     *
     * @param sessionId 实时视频会话编号
     * @param user 当前操作用户
     * @return 对讲启动响应
     */
    @Transactional
    public synchronized IntercomResponse startIntercom(String sessionId, CurrentUser user) {
        VideoSession session = requireOpenSession(lockSessionRuntime(sessionId));
        // 对讲同一时间只能由一个浏览器 client 占用。判断 clientId 可以避免同一用户
        // 开多个页面时互相抢占，心跳超时后 expireIntercom 会释放这些字段。
        if (holdsRoomForIntercom(session)
                && (!Objects.equals(session.getIntercomOperatorId(), user.userId())
                || !Objects.equals(session.getIntercomClientId(), user.clientId()))) {
            throw new IllegalStateException("对讲已被其他操作员占用");
        }
        requireIntercomAvailable(session, user);
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
        return VideoSessionResponses.intercom(session, properties.getLivekit().getUrl(), token.token(), token.expiresAt());
    }

    /**
     * 刷新对讲操作员心跳。
     *
     * @param sessionId 实时视频会话编号
     * @param user 当前操作用户
     * @return 对讲状态响应
     */
    @Transactional
    public synchronized IntercomResponse heartbeatIntercom(String sessionId, CurrentUser user) {
        VideoSession session = requireIntercomOperator(requireOpenSession(lockSessionRuntime(sessionId)), user);
        session.setIntercomHeartbeatAt(now());
        session.setUpdatedAt(now());
        repository.save(session);
        TokenResult token = liveKitTokenService.createOperatorToken(session.getRoomName(), user.userId(), user.clientId());
        return VideoSessionResponses.intercom(session, properties.getLivekit().getUrl(), token.token(), token.expiresAt());
    }

    /**
     * 停止实时视频会话中的对讲。
     *
     * @param sessionId 实时视频会话编号
     * @param user 当前操作用户
     * @return 实时视频会话响应
     */
    @Transactional
    public synchronized VideoSessionResponse stopIntercom(String sessionId, CurrentUser user) {
        VideoSession session = requireIntercomOperator(requireOpenSession(lockSessionRuntime(sessionId)), user);
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
        return VideoSessionResponses.from(session, properties.getLivekit().getUrl(), null);
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
        VideoSession snapshot = requireSession(sessionId);
        // 通道切换本质上是让同一个业务会话指向新的 RTSP/track。
        // requestClientStart 会更新 commandId 并把状态切到 REQUESTING_CLIENT。
        var quality = request.getQuality() == null ? snapshot.getQuality() : request.getQuality();
        VideoSourceRuntime runtime = lockSourceRuntime(
                snapshot.getSourceType(),
                snapshot.getSourceId(),
                snapshot.getDeviceId(),
                request.getChannel(),
                quality,
                roomName(snapshot, request.getChannel(), quality));
        VideoSession session = requireSessionForUpdate(sessionId);
        session.setChannel(request.getChannel());
        session.setQuality(quality);
        session.setRuntimeId(runtime.getRuntimeId());
        session.setRoomName(runtime.getRoomName());
        requestClientStart(session, "video.track.switching", false);
        session.setUpdatedAt(now());
        repository.save(session);
        return VideoSessionResponses.from(session, properties.getLivekit().getUrl(), null);
    }

    @Transactional
    public FileListItemResponse startRecording(String sessionId, CurrentUser user) {
        VideoSession session = lockSessionRuntime(sessionId);
        if (session.getStatus() != VideoSessionStatus.STREAMING && session.getStatus() != VideoSessionStatus.ROOM_READY) {
            throw new IllegalStateException("当前会话未在推流");
        }
        ActiveVideoTrack liveKitTrack = liveKitRoomService
                .resolveActiveVideoTrack(session.getRoomName(), publisherIdentity(session), session.getTrackSid())
                .orElseThrow(() -> new IllegalStateException("房间无预期 Publisher 的活跃推流，无法录制"));
        return fileService.startLiveRecording(session, liveKitTrack.trackSid(), user);
    }

    public FileListItemResponse stopRecording(String sessionId, String fileId, CurrentUser user) {
        return fileService.stopLiveRecording(sessionId, fileId, user);
    }

    public FileListItemResponse activeRecording(String sessionId, CurrentUser user) {
        return fileService.activeLiveRecording(sessionId, user);
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
    public synchronized void handleClientStatus(
            String sessionId,
            String status,
            String trackSid,
            String trackName,
            String errorCode,
            String message) {
        VideoSession session = requireSession(sessionId);
        session.setLastStatusAt(now());
        String normalized = status == null ? "" : status.trim().toLowerCase();
        if (isLateVideoStatus(session, normalized)) {
            emit("video.client.status.ignored", Map.of(
                    "sessionId", sessionId,
                    "status", normalized,
                    "reason", "session has no viewer or intercom owner"));
            session.setUpdatedAt(now());
            repository.save(session);
            return;
        }
        // Go 客户端通过 MQTT 回报的是轻量字符串状态，这里统一映射为后端状态机枚举，
        // 并在关键节点发布 WebSocket 事件，驱动前端刷新。
        switch (normalized) {
            case "room_ready", "publishing", "streaming", "track_published" -> {
                // 设备状态只能证明本地发布流程已运行，不能覆盖已经确认的 LiveKit 媒体事实。
                if (session.getStatus() != VideoSessionStatus.STREAMING) {
                    session.setTrackName(trackName == null || trackName.isBlank()
                            ? "video." + session.getChannel() + "." + session.getQuality()
                            : trackName);
                    transition(session, VideoSessionStatus.ROOM_READY, "video.room.ready", session);
                }
            }
            case "interrupted" -> {
                if (session.getSourceType() == VideoSourceType.FIXED_CAMERA
                        && session.getStatus() != VideoSessionStatus.STREAMING) {
                    markFailed(
                            session,
                            errorCode == null ? "PUBLISH_PROCESS_EXITED" : errorCode,
                            safeMessage(message),
                            "video.session.failed");
                } else {
                    emit("video.client.interrupted", Map.of(
                            "sessionId", sessionId,
                            "message", safeMessage(message)));
                }
            }
            case "stopped", "closed" -> {
                // 主动释放会先在 Media 内关闭会话；意外退出由 LiveKit Track 对账确认后中断。
                if (session.getStatus() == VideoSessionStatus.STOPPING
                        || session.getStatus() == VideoSessionStatus.CLOSED) {
                    session.setEndedAt(now());
                    transition(session, VideoSessionStatus.CLOSED, "video.session.closed", session);
                } else {
                    emit("video.client.stopped", Map.of("sessionId", sessionId));
                }
            }
            case "failed", "error" -> {
                if (session.getStatus() == VideoSessionStatus.STREAMING) {
                    emit("video.client.failed", Map.of(
                            "sessionId", sessionId,
                            "errorCode", errorCode == null ? "CLIENT_STATUS_FAILED" : errorCode,
                            "message", safeMessage(message)));
                } else {
                    markFailed(
                            session,
                            errorCode == null ? "CLIENT_STATUS_FAILED" : errorCode,
                            safeMessage(message),
                            "video.session.failed");
                }
            }
            default -> emit("video.client.status", Map.of(
                    "sessionId", sessionId,
                    "status", safeMessage(status),
                    "message", safeMessage(message)));
        }
        session.setUpdatedAt(now());
        repository.save(session);
    }

    /**
     * 通过 LiveKit Room API 确认固定摄像头真实视频轨道。
     *
     * @param sessionId 视频会话编号
     * @return 已存在真实视频轨道时返回 {@code true}
     */
    @Transactional
    public synchronized boolean confirmFixedCameraTrack(String sessionId) {
        VideoSession session = requireSession(sessionId);
        if (session.getSourceType() != VideoSourceType.FIXED_CAMERA
                || session.getStatus() != VideoSessionStatus.ROOM_READY) {
            return session.getStatus() == VideoSessionStatus.STREAMING;
        }
        Optional<ActiveVideoTrack> track;
        try {
            track = liveKitRoomService.resolveActiveVideoTrack(
                    session.getRoomName(), publisherIdentity(session), session.getTrackSid());
        } catch (RuntimeException exception) {
            log.warn("确认固定摄像头 LiveKit 视频轨道失败，sessionId={}", sessionId, exception);
            return false;
        }
        if (track.isEmpty()) {
            return false;
        }
        ActiveVideoTrack activeTrack = track.get();
        session.setTrackSid(activeTrack.trackSid());
        session.setTrackName(activeTrack.trackName() == null || activeTrack.trackName().isBlank()
                ? "video." + session.getChannel() + "." + session.getQuality()
                : activeTrack.trackName());
        mediaTrackService.publish(
                session, activeTrack.participantIdentity(), session.getTrackSid(), session.getTrackName());
        session.setStartedAt(session.getStartedAt() == null ? now() : session.getStartedAt());
        transition(session, VideoSessionStatus.STREAMING, "video.session.streaming", session);
        session.setUpdatedAt(now());
        repository.save(session);
        return true;
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
    public synchronized void handleIntercomStatus(
            String sessionId,
            String status,
            String robotAudioTrackSid,
            String robotAudioTrackName,
            String errorCode,
            String message) {
        VideoSession session = requireSession(sessionId);
        String normalized = status == null ? "" : status.trim().toLowerCase();
        if (("starting".equals(normalized) || "active".equals(normalized))
                && !holdsRoomForIntercom(session)) {
            log.info("忽略已释放对讲会话的迟到状态，sessionId={} currentStatus={} reportedStatus={}",
                    sessionId, session.getIntercomStatus(), normalized);
            return;
        }
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
                emit("video.intercom.interrupted", Map.of(
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
                emit("video.intercom.failed", Map.of(
                        "sessionId", sessionId,
                        "errorCode", errorCode == null ? "INTERCOM_FAILED" : errorCode,
                        "message", safeMessage(message)));
            }
            default -> emit("video.intercom.status", Map.of(
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
                        return requestClientStart(
                                requireSessionForUpdate(session.getSessionId()),
                                "video.client.online_restart",
                                false);
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 当前用户手动重启实时视频会话。
     *
     * <p>重启只处理 Publisher，不改变 viewer 占用；观看关系由 create/heartbeat/stop 维护。</p>
     *
     * @param sessionId 实时视频会话编号
     * @param user 当前操作用户
     * @return 实时视频会话响应
     */
    @Transactional
    public VideoSessionResponse restartSession(String sessionId, CurrentUser user) {
        VideoSession session = requireSessionForUpdate(sessionId);
        requestClientStart(session, "video.session.restart", false);
        return VideoSessionResponses.from(session, properties.getLivekit().getUrl(), null);
    }

    /**
     * 后台任务自动重启实时视频会话。
     *
     * @param sessionId 实时视频会话编号
     */
    @Transactional
    public void restartSession(String sessionId) {
        requestClientStart(requireSessionForUpdate(sessionId), "video.session.auto_restart", false);
    }

    /**
     * 当前用户手动重启实时视频会话，并返回待下发的视频启动命令。
     *
     * <p>重启只处理 Publisher，不改变 viewer 占用；避免与观看心跳形成反向锁序。</p>
     *
     * @param sessionId 实时视频会话编号
     * @param user 当前操作用户
     * @return 视频启动命令
     */
    @Transactional
    public VideoStartCommand restartSessionCommand(String sessionId, CurrentUser user) {
        VideoSession session = requireSessionForUpdate(sessionId);
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
        return requestClientStart(requireSessionForUpdate(sessionId), "video.session.auto_restart", false);
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
        return requestClientStart(requireSessionForUpdate(sessionId), event, true);
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
        if (startRequestInFlight(session)) {
            log.info("复用进行中的视频启动命令，sessionId={} commandId={} status={}",
                    session.getSessionId(), session.getCommandId(), session.getStatus());
            return createStartCommand(session);
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
                session.getRoomName(), publisherIdentity(session));
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
                session.getSourceType(),
                session.getSourceId(),
                session.getDeviceId(),
                session.getChannel(),
                session.getQuality(),
                properties.getLivekit().getUrl(),
                session.getRoomName(),
                publisherToken.token(),
                publisherIdentity(session),
                null,
                publisherToken.expiresAt());
    }

    private VideoStartCommand createStartCommand(VideoSession session) {
        TokenResult publisherToken = liveKitTokenService.createPublisherToken(
                session.getRoomName(), publisherIdentity(session));
        return new VideoStartCommand(
                session.getCommandId(),
                session.getSessionId(),
                session.getRobotId(),
                session.getSourceType(),
                session.getSourceId(),
                session.getDeviceId(),
                session.getChannel(),
                session.getQuality(),
                properties.getLivekit().getUrl(),
                session.getRoomName(),
                publisherToken.token(),
                publisherIdentity(session),
                null,
                publisherToken.expiresAt());
    }

    private boolean startRequestInFlight(VideoSession session) {
        if (session.getCommandId() == null || session.getCommandId().isBlank()
                || session.getCommandRequestedAt() == null) {
            return false;
        }
        boolean waitingForTrack = session.getStatus() == VideoSessionStatus.REQUESTING_CLIENT
                || session.getStatus() == VideoSessionStatus.ROOM_READY;
        OffsetDateTime deadline = session.getCommandRequestedAt()
                .plusSeconds(properties.getSession().getTrackPublishTimeoutSeconds());
        return waitingForTrack && deadline.isAfter(now());
    }

    private String publisherIdentity(VideoSession session) {
        if (session.getSourceType() == VideoSourceType.FIXED_CAMERA) {
            return "fixed-camera:" + session.getSourceId();
        }
        return "robot:" + session.getRobotId() + ":" + session.getDeviceId();
    }

    private String publisherIdentity(VideoSourceRuntime runtime) {
        if (runtime.getSourceType() == VideoSourceType.FIXED_CAMERA) {
            return "fixed-camera:" + runtime.getSourceId();
        }
        return "robot:" + runtime.getSourceId() + ":" + runtime.getDeviceId();
    }

    /**
     * 标记实时视频会话推流超时。
     *
     * @param sessionId 实时视频会话编号
     * @param expectedCommandId 超时任务扫描到的启动命令编号
     * @param errorCode 错误码
     * @param message 错误说明
     */
    @Transactional
    public void markTimeout(String sessionId, String expectedCommandId, String errorCode, String message) {
        VideoSession session = requireSessionForUpdate(sessionId);
        OffsetDateTime threshold = now().minusSeconds(properties.getSession().getTrackPublishTimeoutSeconds());
        boolean waitingForTrack = session.getStatus() == VideoSessionStatus.REQUESTING_CLIENT
                || session.getStatus() == VideoSessionStatus.ROOM_READY;
        if (!waitingForTrack
                || !Objects.equals(session.getCommandId(), expectedCommandId)
                || session.getCommandRequestedAt() == null
                || session.getCommandRequestedAt().isAfter(threshold)) {
            log.info("忽略已过期的视频超时任务，sessionId={} expectedCommandId={} currentCommandId={} status={}",
                    sessionId, expectedCommandId, session.getCommandId(), session.getStatus());
            return;
        }
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
    @org.springframework.transaction.annotation.Transactional(
            isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Map<String, Object> releaseIdleSession(String sessionId) {
        VideoSession snapshot = requireSession(sessionId);
        if (snapshot.getStatus() != VideoSessionStatus.IDLE_WAIT) {
            return Map.of();
        }

        VideoSession session = lockSessionRuntime(sessionId);
        if (session.getStatus() != VideoSessionStatus.IDLE_WAIT) {
            return Map.of();
        }
        List<VideoSession> roomSessions = new ArrayList<>(
                repository.findByRuntimeIdOrderBySessionIdAsc(session.getRuntimeId()));
        roomSessions.addAll(repository
                .findByRuntimeIdIsNullAndSourceTypeAndSourceIdAndDeviceIdAndChannelAndQualityOrderBySessionIdAsc(
                        session.getSourceType(),
                        session.getSourceId(),
                        session.getDeviceId(),
                        session.getChannel(),
                        session.getQuality()));
        int targetViewerCount = activeViewerCount(sessionId);
        session.setViewerCount(targetViewerCount);
        if (targetViewerCount > 0
                || holdsRoomForIntercom(session)
                || fileService.hasActiveLiveRecording(sessionId)) {
            repository.save(session);
            return Map.of();
        }

        OffsetDateTime idleBefore = now().minusSeconds(properties.getSession().getIdleReleaseDelaySeconds());
        boolean roomInUse = roomSessions.stream()
                .filter(candidate -> !candidate.getSessionId().equals(sessionId))
                .anyMatch(candidate -> activeViewerCount(candidate.getSessionId()) > 0
                        || holdsRoomForIntercom(candidate)
                        || fileService.hasActiveLiveRecording(candidate.getSessionId())
                        || startRequestInFlight(candidate)
                        || !idleDelayElapsed(candidate, idleBefore));
        if (roomInUse) {
            closeSessionRecord(session);
            return Map.of();
        }

        roomSessions.stream()
                .filter(candidate -> candidate.getStatus() != VideoSessionStatus.CLOSED)
                .forEach(this::closeSessionRecord);
        Map<String, Object> stopPayload = Map.of(
                "robotId", session.getRobotId(),
                "sourceType", session.getSourceType().name(),
                "sourceId", session.getSourceId(),
                "deviceId", session.getDeviceId(),
                "sessionId", session.getSessionId(),
                "commandId", "cmd_" + compactUuid(),
                "roomName", session.getRoomName());
        liveKitRoomService.deleteRoom(session.getRoomName());
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
                .map(session -> VideoSessionResponses.from(session, properties.getLivekit().getUrl(), null))
                .toList();
    }

    /**
     * 查询当前可复用的实时视频会话。
     *
     * @return 活跃实时视频会话列表
     */
    public List<VideoSessionResponse> active() {
        return repository.findTop16ByStatusInOrderByUpdatedAtDesc(REUSABLE_STATUSES).stream()
                .map(session -> VideoSessionResponses.from(session, properties.getLivekit().getUrl(), null))
                .toList();
    }

    /**
     * 查询需要自动重启的中断会话。
     *
     * @param updatedBefore 更新时间阈值
     * @return 会话编号列表
     */
    public List<String> interruptedRestartCandidates(OffsetDateTime interruptedBefore) {
        // viewer 心跳会刷新 updatedAt，不能用它衡量断流已持续多久；lastStatusAt 只由客户端状态上报刷新。
        return repository.findByStatusAndLastStatusAtBefore(VideoSessionStatus.INTERRUPTED, interruptedBefore).stream()
                .filter(session -> session.getViewerCount() > 0
                        || holdsRoomForIntercom(session)
                        || fileService.hasActiveLiveRecording(session.getSessionId()))
                .map(VideoSession::getSessionId)
                .toList();
    }

    /**
     * 周期核对所有等待发布、推流中和中断中的 SourceRuntime，以补偿 Webhook 漏投。
     */
    public void reconcileLiveKitTracks() {
        repository.findDistinctRuntimeIdsByStatusIn(RECONCILE_STATUSES).forEach(runtimeId -> {
            try {
                reconcileLiveKitRuntime(runtimeId);
            } catch (RuntimeException exception) {
                log.warn("LiveKit Track 周期对账失败 runtimeId={}", runtimeId, exception);
            }
        });
    }

    /**
     * Webhook 到达后按 Room 定位唯一 SourceRuntime，并以 Room API 当前事实完成幂等对账。
     */
    public void reconcileLiveKitRoom(String roomName) {
        sourceRuntimeRepository.findByRoomName(roomName)
                .ifPresent(runtime -> reconcileLiveKitRuntime(runtime.getRuntimeId()));
    }

    void reconcileLiveKitRuntime(String runtimeId) {
        VideoSourceRuntime snapshot = sourceRuntimeRepository.findById(runtimeId).orElse(null);
        if (snapshot == null) {
            return;
        }
        OffsetDateTime observedAt = now();
        Optional<ActiveVideoTrack> observed = liveKitRoomService.resolveActiveVideoTrack(
                snapshot.getRoomName(), publisherIdentity(snapshot), snapshot.getTrackSid());
        transactionTemplate.executeWithoutResult(status -> applyLiveKitObservation(runtimeId, observedAt, observed));
    }

    private void applyLiveKitObservation(
            String runtimeId,
            OffsetDateTime observedAt,
            Optional<ActiveVideoTrack> observed) {
        VideoSourceRuntime runtime = sourceRuntimeRepository.findByIdForUpdate(runtimeId).orElse(null);
        if (runtime == null) {
            return;
        }
        List<VideoSession> sessions = repository.findByRuntimeIdOrderBySessionIdAsc(runtimeId);
        boolean runtimeChanged;
        if (observed.isPresent()) {
            ActiveVideoTrack track = observed.get();
            runtimeChanged = !Objects.equals(runtime.getPublisherIdentity(), track.participantIdentity())
                    || !Objects.equals(runtime.getPublisherParticipantSid(), track.participantSid())
                    || !Objects.equals(runtime.getTrackSid(), track.trackSid())
                    || !Objects.equals(runtime.getTrackName(), track.trackName());
            runtime.setPublisherIdentity(track.participantIdentity());
            runtime.setPublisherParticipantSid(track.participantSid());
            runtime.setTrackSid(track.trackSid());
            runtime.setTrackName(track.trackName());
            if (runtimeChanged) {
                runtime.setLastMediaAt(observedAt);
            }
            sessions.stream()
                    .filter(session -> RECONCILE_STATUSES.contains(session.getStatus()))
                    .forEach(session -> applyPublishedTrack(session, track, observedAt));
        } else {
            runtimeChanged = runtime.getPublisherIdentity() != null
                    || runtime.getPublisherParticipantSid() != null
                    || runtime.getTrackSid() != null
                    || runtime.getTrackName() != null;
            runtime.setPublisherIdentity(null);
            runtime.setPublisherParticipantSid(null);
            runtime.setTrackSid(null);
            runtime.setTrackName(null);
            sessions.stream()
                    .filter(session -> (session.getStatus() == VideoSessionStatus.STREAMING
                            || session.getStatus() == VideoSessionStatus.IDLE_WAIT)
                            && session.getTrackSid() != null)
                    .forEach(session -> applyMissingTrack(session, observedAt));
        }
        if (runtimeChanged) {
            runtime.setUpdatedAt(observedAt);
            sourceRuntimeRepository.save(runtime);
        }
    }

    private void applyPublishedTrack(
            VideoSession session,
            ActiveVideoTrack track,
            OffsetDateTime observedAt) {
        String resolvedTrackName = track.trackName() == null || track.trackName().isBlank()
                ? "video." + session.getChannel() + "." + session.getQuality()
                : track.trackName();
        VideoSessionStatus targetStatus = session.getStatus() == VideoSessionStatus.IDLE_WAIT
                ? VideoSessionStatus.IDLE_WAIT
                : VideoSessionStatus.STREAMING;
        boolean changed = session.getStatus() != targetStatus
                || !Objects.equals(session.getTrackSid(), track.trackSid())
                || !Objects.equals(session.getTrackName(), resolvedTrackName);
        if (!changed) {
            return;
        }
        if (session.getTrackSid() != null && !Objects.equals(session.getTrackSid(), track.trackSid())) {
            mediaTrackService.unpublish(session);
        }
        session.setTrackSid(track.trackSid());
        session.setTrackName(resolvedTrackName);
        session.setStartedAt(session.getStartedAt() == null ? observedAt : session.getStartedAt());
        session.setEndedAt(null);
        session.setLastErrorCode(null);
        session.setLastErrorMessage(null);
        mediaTrackService.publish(
                session, track.participantIdentity(), session.getTrackSid(), session.getTrackName());
        if (targetStatus == VideoSessionStatus.STREAMING) {
            transition(session, VideoSessionStatus.STREAMING, "video.session.streaming", session);
        } else {
            session.setUpdatedAt(observedAt);
        }
        repository.save(session);
    }

    private void applyMissingTrack(VideoSession session, OffsetDateTime observedAt) {
        boolean wasStreaming = session.getStatus() == VideoSessionStatus.STREAMING;
        mediaTrackService.unpublish(session);
        session.setTrackSid(null);
        session.setTrackName(null);
        session.setViewerCount(activeViewerCount(session.getSessionId()));
        session.setLastStatusAt(observedAt);
        if (session.getViewerCount() > 0
                || holdsRoomForIntercom(session)
                || fileService.hasActiveLiveRecording(session.getSessionId())) {
            transition(session, VideoSessionStatus.INTERRUPTED, "video.session.interrupted", Map.of(
                    "sessionId", session.getSessionId(),
                    "message", "LiveKit Publisher/Track missing"));
        } else if (wasStreaming) {
            session.setIdleSince(observedAt);
            transition(session, VideoSessionStatus.IDLE_WAIT, "video.session.idle_wait", Map.of(
                    "sessionId", session.getSessionId(),
                    "idleReleaseDelaySeconds", properties.getSession().getIdleReleaseDelaySeconds()));
        } else {
            session.setUpdatedAt(observedAt);
        }
        repository.save(session);
    }

    /**
     * 查询需要释放的空闲会话。
     *
     * @param idleSinceBefore 空闲开始时间阈值
     * @return 会话编号列表
     */
    public List<String> idleReleaseCandidates(OffsetDateTime idleSinceBefore) {
        return repository.findByStatusAndIdleSinceBefore(VideoSessionStatus.IDLE_WAIT, idleSinceBefore).stream()
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
        return repository.findIntercomTimeoutCandidates(
                        Set.of(IntercomStatus.STARTING, IntercomStatus.ACTIVE), heartbeatBefore).stream()
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
    public synchronized Map<String, Object> expireIntercom(String sessionId) {
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
        emit("video.intercom.interrupted", Map.of(
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

    /** 每个过期 viewer 独立事务，按 runtime -> session 锁序与心跳、停看串行化。 */
    public void sweepStaleViewers() {
        OffsetDateTime threshold = now().minusSeconds(properties.getSession().getViewerHeartbeatTimeoutSeconds());
        viewerRepository.findByLeftAtIsNullAndLastHeartbeatAtBefore(threshold).forEach(viewer -> {
            try {
                boolean closed = Boolean.TRUE.equals(transactionTemplate.execute(
                        status -> closeStaleViewer(viewer, threshold)));
                if (closed) {
                    stopClientRecordingQuietly(viewer.getSessionId(), viewer.getUserId(), viewerClientId(viewer));
                }
            } catch (RuntimeException ex) {
                log.warn("清理过期观看租约失败 sessionId={} viewerId={}",
                        viewer.getSessionId(), viewer.getId(), ex);
            }
        });
    }

    /** 收口没有活跃 viewer 的历史及异常会话，避免它们因缺少离开事件永久滞留。 */
    public void sweepUnoccupiedSessions() {
        repository.findUnoccupiedSessionIds(UNOCCUPIED_RECONCILE_STATUSES, PageRequest.of(0, 100))
                .forEach(sessionId -> {
                    try {
                        transactionTemplate.executeWithoutResult(status -> moveUnoccupiedSessionToIdle(sessionId));
                    } catch (RuntimeException ex) {
                        log.warn("收口无观看者视频会话失败 sessionId={}", sessionId, ex);
                    }
                });
    }

    private void moveUnoccupiedSessionToIdle(String sessionId) {
        VideoSession session = lockSessionRuntime(sessionId);
        session.setViewerCount(activeViewerCount(sessionId));
        if (enterIdleWhenUnoccupied(session)) {
            session.setUpdatedAt(now());
            repository.save(session);
        }
    }

    private boolean closeStaleViewer(MediaSessionViewer viewer, OffsetDateTime heartbeatBefore) {
        VideoSession session = lockSessionRuntime(viewer.getSessionId());
        if (viewerRepository.closeIfStale(viewer.getId(), heartbeatBefore, now()) == 0) {
            return false;
        }
        session.setViewerCount(activeViewerCount(session.getSessionId()));
        if (!enterIdleWhenUnoccupied(session)) {
            emit("video.viewer.changed", session);
        }
        session.setUpdatedAt(now());
        repository.save(session);
        return true;
    }

    private boolean enterIdleWhenUnoccupied(VideoSession session) {
        if (session.getViewerCount() != 0 || holdsRoomForIntercom(session)
                || fileService.hasActiveLiveRecording(session.getSessionId())
                || session.getStatus() == VideoSessionStatus.IDLE_WAIT
                || session.getStatus() == VideoSessionStatus.CLOSED
                || session.getStatus() == VideoSessionStatus.STOPPING) {
            return false;
        }
        session.setIdleSince(now());
        transition(session, VideoSessionStatus.IDLE_WAIT, "video.session.idle_wait", Map.of(
                "sessionId", session.getSessionId(),
                "idleReleaseDelaySeconds", properties.getSession().getIdleReleaseDelaySeconds()));
        return true;
    }

    private VideoSession requireSession(String sessionId) {
        return repository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("未找到视频会话：" + sessionId));
    }

    private VideoSession requireSessionForUpdate(String sessionId) {
        return repository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("未找到视频会话：" + sessionId));
    }

    /**
     * 按 runtime -> session 的固定顺序锁定会话所属 Room。
     *
     * <p>旧会话没有 runtimeId 时在原位补齐；如果并发切换已改变 runtime，调用方应重试，
     * 不能在持有旧 runtime 锁时继续修改新 Room。</p>
     */
    private VideoSession lockSessionRuntime(String sessionId) {
        VideoSession snapshot = requireSession(sessionId);
        VideoSourceRuntime runtime;
        if (snapshot.getRuntimeId() == null || snapshot.getRuntimeId().isBlank()) {
            runtime = lockSourceRuntime(
                    snapshot.getSourceType(),
                    snapshot.getSourceId(),
                    snapshot.getDeviceId(),
                    snapshot.getChannel(),
                    snapshot.getQuality(),
                    roomName(snapshot));
        } else {
            runtime = sourceRuntimeRepository.findByIdForUpdate(snapshot.getRuntimeId())
                    .orElseThrow(() -> new IllegalStateException("未找到媒体源运行态：" + snapshot.getRuntimeId()));
        }
        VideoSession session = requireSessionForUpdate(sessionId);
        entityManager.refresh(session, LockModeType.PESSIMISTIC_WRITE);
        if (session.getRuntimeId() != null
                && !session.getRuntimeId().isBlank()
                && !Objects.equals(session.getRuntimeId(), runtime.getRuntimeId())) {
            throw new IllegalStateException("视频会话运行态已变化，请重试");
        }
        session.setRuntimeId(runtime.getRuntimeId());
        session.setRoomName(runtime.getRoomName());
        return session;
    }

    private VideoSession requireIntercomOperator(String sessionId, CurrentUser user) {
        return requireIntercomOperator(requireSession(sessionId), user);
    }

    private VideoSession requireIntercomOperator(VideoSession session, CurrentUser user) {
        if (!Objects.equals(session.getIntercomOperatorId(), user.userId())
                || !Objects.equals(session.getIntercomClientId(), user.clientId())) {
            throw new IllegalStateException("当前用户未持有对讲权限");
        }
        return session;
    }

    private VideoSession requireOpenSession(VideoSession session) {
        if (session.getStatus() == VideoSessionStatus.CLOSED
                || session.getStatus() == VideoSessionStatus.STOPPING) {
            throw new IllegalStateException("视频会话已关闭");
        }
        return session;
    }

    private boolean holdsRoomForIntercom(VideoSession session) {
        return session.getIntercomStatus() == IntercomStatus.STARTING
                || session.getIntercomStatus() == IntercomStatus.ACTIVE;
    }

    private boolean idleDelayElapsed(VideoSession session, OffsetDateTime idleBefore) {
        if (session.getStatus() == VideoSessionStatus.CLOSED) {
            return true;
        }
        OffsetDateTime idleAt = session.getIdleSince() != null
                ? session.getIdleSince()
                : session.getUpdatedAt() != null ? session.getUpdatedAt() : session.getCreatedAt();
        return idleAt != null && !idleAt.isAfter(idleBefore);
    }

    private void closeSessionRecord(VideoSession session) {
        if (session.getStatus() == VideoSessionStatus.CLOSED) {
            return;
        }
        transition(session, VideoSessionStatus.STOPPING, "video.session.stopping", session);
        mediaTrackService.unpublish(session);
        session.setViewerCount(activeViewerCount(session.getSessionId()));
        session.setEndedAt(now());
        transition(session, VideoSessionStatus.CLOSED, "video.session.closed", session);
        repository.save(session);
    }

    private boolean isLateVideoStatus(VideoSession session, String status) {
        boolean changesVideoState = "room_ready".equals(status)
                || "publishing".equals(status)
                || "streaming".equals(status)
                || "track_published".equals(status)
                || "interrupted".equals(status)
                || "failed".equals(status)
                || "error".equals(status);
        if (!changesVideoState) {
            return false;
        }
        if (session.getStatus() == VideoSessionStatus.STOPPING
                || session.getStatus() == VideoSessionStatus.CLOSED) {
            return true;
        }
        return session.getStatus() == VideoSessionStatus.IDLE_WAIT
                && activeViewerCount(session.getSessionId()) == 0
                && !holdsRoomForIntercom(session);
    }

    private void requireIntercomAvailable(VideoSession target, CurrentUser user) {
        Set<IntercomStatus> occupiedStatuses = Set.of(
                IntercomStatus.STARTING,
                IntercomStatus.ACTIVE);
        repository.findByIntercomStatusIn(occupiedStatuses).stream()
                .filter(session -> !session.getSessionId().equals(target.getSessionId()))
                .forEach(session -> {
                    if (Objects.equals(session.getRobotId(), target.getRobotId())) {
                        throw new IllegalStateException("该机器人正在进行其他对讲");
                    }
                    if (Objects.equals(session.getIntercomOperatorId(), user.userId())) {
                        throw new IllegalStateException("当前操作员正在与其他机器人通话，请先结束当前通话");
                    }
                    if (Objects.equals(session.getIntercomClientId(), user.clientId())) {
                        throw new IllegalStateException("当前终端正在与其他机器人通话，请先结束当前通话");
                    }
                });
    }

    private String roomName(CreateVideoSessionRequest request) {
        if (request.getSourceType() == VideoSourceType.FIXED_CAMERA) {
            return "media.fixed." + request.getSourceId() + "." + request.getChannel() + "." + request.getQuality();
        }
        return "media." + request.getRobotId() + "." + request.getDeviceId() + "." + request.getChannel() + "." + request.getQuality();
    }

    private String roomName(VideoSession session) {
        return roomName(session, session.getChannel(), session.getQuality());
    }

    private String roomName(
            VideoSession session,
            VideoChannel channel,
            VideoQuality quality) {
        if (session.getSourceType() == VideoSourceType.FIXED_CAMERA) {
            return "media.fixed." + session.getSourceId() + "." + channel + "." + quality;
        }
        return "media." + session.getRobotId() + "." + session.getDeviceId() + "." + channel + "." + quality;
    }

    private VideoSourceRuntime lockSourceRuntime(
            VideoSourceType sourceType,
            String sourceId,
            String deviceId,
            VideoChannel channel,
            VideoQuality quality,
            String roomName) {
        String runtimeKey = sourceType + ":" + sourceId + ":" + deviceId + ":" + channel + ":" + quality;
        String runtimeId = "runtime_" + UUID.nameUUIDFromBytes(runtimeKey.getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "");
        OffsetDateTime timestamp = now();
        sourceRuntimeRepository.insertIfAbsent(
                runtimeId,
                sourceType.name(),
                sourceId,
                deviceId,
                channel.name(),
                quality.name(),
                roomName,
                timestamp);
        return sourceRuntimeRepository.findBySourceForUpdate(sourceType, sourceId, deviceId, channel, quality)
                .orElseThrow(() -> new IllegalStateException("媒体源运行态创建失败：" + runtimeKey));
    }

    private void emit(String event, Object data) {
        webSocketPublisher.publish(event, data);
    }

    private void transition(VideoSession session, VideoSessionStatus targetStatus, String event, Object payload) {
        session.setStatus(targetStatus);
        session.setUpdatedAt(now());
        emit(event, payload);
    }

    private void markFailed(VideoSession session, String errorCode, String message, String event) {
        String persistedErrorCode = truncate(errorCode, LAST_ERROR_CODE_MAX_LENGTH);
        String persistedMessage = truncate(message, LAST_ERROR_MESSAGE_MAX_LENGTH);
        session.setStatus(VideoSessionStatus.FAILED);
        session.setLastErrorCode(persistedErrorCode);
        session.setLastErrorMessage(persistedMessage);
        emit(event, Map.of(
                "sessionId", session.getSessionId(),
                "errorCode", persistedErrorCode,
                "message", persistedMessage));
    }

    private String safeMessage(String message) {
        return truncate(message, LAST_ERROR_MESSAGE_MAX_LENGTH);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        int codePointCount = value.codePointCount(0, value.length());
        return codePointCount <= maxLength
                ? value
                : value.substring(0, value.offsetByCodePoints(0, maxLength));
    }

    private void addViewer(VideoSession session, CurrentUser user) {
        String identity = viewerIdentity(user);
        // 同一浏览器 client 重复心跳时只刷新 lastHeartbeatAt；新的标签页会得到不同 clientId，
        // 因而会被计为独立 viewer。
        viewerRepository.findFirstBySessionIdAndParticipantIdentityAndLeftAtIsNull(session.getSessionId(), identity)
                .map(viewer -> {
                    viewer.setClientId(user.clientId());
                    viewer.setLastHeartbeatAt(now());
                    viewer.setActiveLeaseKey(identity);
                    return viewerRepository.save(viewer);
                })
                .orElseGet(() -> {
                    MediaSessionViewer viewer = new MediaSessionViewer();
                    viewer.setId("viewer_" + compactUuid());
                    viewer.setSessionId(session.getSessionId());
                    viewer.setUserId(user.userId());
                    viewer.setOrgId(user.orgId());
                    viewer.setParticipantIdentity(identity);
                    viewer.setActiveLeaseKey(identity);
                    viewer.setClientId(user.clientId());
                    viewer.setClientType("web");
                    viewer.setJoinedAt(now());
                    viewer.setLastHeartbeatAt(now());
                    return viewerRepository.save(viewer);
                });
    }

    private void removeViewer(String sessionId, CurrentUser user) {
        viewerRepository.closeActiveLease(sessionId, viewerIdentity(user), now());
    }

    private int activeViewerCount(String sessionId) {
        return Math.toIntExact(viewerRepository.countBySessionIdAndLeftAtIsNull(sessionId));
    }

    private String viewerIdentity(CurrentUser user) {
        return "user:" + user.userId() + ":" + user.clientId();
    }

    private String viewerClientId(MediaSessionViewer viewer) {
        if (viewer.getClientId() != null && !viewer.getClientId().isBlank()) {
            return viewer.getClientId();
        }
        String identity = viewer.getParticipantIdentity();
        if (identity == null) {
            return null;
        }
        int marker = identity.lastIndexOf(":");
        return marker < 0 || marker == identity.length() - 1 ? null : identity.substring(marker + 1);
    }

    private void stopClientRecordingQuietly(String sessionId, String userId, String clientId) {
        try {
            fileService.stopLiveRecordingForClient(sessionId, userId, clientId);
        } catch (Exception ex) {
            log.warn("停止观看端录像失败 sessionId={}, userId={}, clientId={}", sessionId, userId, clientId, ex);
        }
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
