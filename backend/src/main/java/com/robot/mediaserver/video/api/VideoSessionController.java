package com.robot.mediaserver.video.api;

import com.robot.mediaserver.auth.CurrentUser;
import com.robot.mediaserver.auth.CurrentUserResolver;
import com.robot.mediaserver.recording.dto.RecordingListItemResponse;
import com.robot.mediaserver.video.dto.CreateSnapshotRequest;
import com.robot.mediaserver.video.dto.CreateVideoSessionRequest;
import com.robot.mediaserver.video.dto.MediaEventLogResponse;
import com.robot.mediaserver.video.dto.MediaTrackResponse;
import com.robot.mediaserver.video.dto.IntercomResponse;
import com.robot.mediaserver.video.dto.SnapshotListResponse;
import com.robot.mediaserver.video.dto.SnapshotResponse;
import com.robot.mediaserver.video.dto.SwitchChannelRequest;
import com.robot.mediaserver.video.dto.VideoSessionResponse;
import com.robot.mediaserver.video.dto.ViewerTokenResponse;
import com.robot.mediaserver.video.event.MediaEventLogService;
import com.robot.mediaserver.video.messaging.VideoStartCommand;
import com.robot.mediaserver.video.messaging.VideoStatusMessage;
import com.robot.mediaserver.video.messaging.IntercomStartCommand;
import com.robot.mediaserver.video.messaging.IntercomStatusMessage;
import com.robot.mediaserver.video.service.SnapshotService;
import com.robot.mediaserver.video.service.MediaTrackService;
import com.robot.mediaserver.video.service.VideoSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/internal/media/video-sessions")
public class VideoSessionController {

    private static final Logger log = LoggerFactory.getLogger(VideoSessionController.class);

    private final VideoSessionService service;
    private final CurrentUserResolver currentUserResolver;
    private final MediaEventLogService eventLogService;
    private final SnapshotService snapshotService;
    private final MediaTrackService mediaTrackService;

    public VideoSessionController(
            VideoSessionService service,
            CurrentUserResolver currentUserResolver,
            MediaEventLogService eventLogService,
            SnapshotService snapshotService,
            MediaTrackService mediaTrackService) {
        this.service = service;
        this.currentUserResolver = currentUserResolver;
        this.eventLogService = eventLogService;
        this.snapshotService = snapshotService;
        this.mediaTrackService = mediaTrackService;
    }

    @PostMapping
    public VideoSessionResponse create(@Valid @RequestBody CreateVideoSessionRequest request, HttpServletRequest servletRequest) {
        log.info("create video session robotId={}, deviceId={}, channel={}, quality={}",
                request.getRobotId(),
                request.getDeviceId(),
                request.getChannel(),
                request.getQuality());
        return service.create(request, currentUserResolver.resolve(servletRequest));
    }

    @PostMapping("/intercom")
    public IntercomResponse createIntercom(
            @Valid @RequestBody CreateVideoSessionRequest request,
            HttpServletRequest servletRequest) {
        return service.createForIntercom(request, currentUserResolver.resolve(servletRequest));
    }

    @GetMapping
    public List<VideoSessionResponse> recent(HttpServletRequest servletRequest) {
        return service.recent(currentUserResolver.resolve(servletRequest));
    }

    @GetMapping("/active")
    public List<VideoSessionResponse> active() {
        return service.active();
    }

    @GetMapping("/interrupted-restart-candidates")
    public List<String> interruptedRestartCandidates(@RequestParam OffsetDateTime before) {
        return service.interruptedRestartCandidates(before);
    }

    @GetMapping("/idle-release-candidates")
    public List<String> idleReleaseCandidates(@RequestParam OffsetDateTime before) {
        return service.idleReleaseCandidates(before);
    }

    @GetMapping("/intercom-timeout-candidates")
    public List<String> intercomTimeoutCandidates(@RequestParam OffsetDateTime before) {
        return service.intercomTimeoutCandidates(before);
    }

    @GetMapping("/{sessionId}")
    public VideoSessionResponse get(@PathVariable String sessionId, HttpServletRequest servletRequest) {
        return service.get(sessionId, currentUserResolver.resolve(servletRequest));
    }

    @GetMapping("/{sessionId}/events")
    public List<MediaEventLogResponse> events(@PathVariable String sessionId) {
        return eventLogService.recentEvents(sessionId);
    }

    @GetMapping("/{sessionId}/tracks")
    public List<MediaTrackResponse> tracks(@PathVariable String sessionId) {
        return mediaTrackService.recentBySession(sessionId);
    }

    @PostMapping("/status")
    public void status(@RequestBody VideoStatusMessage status) {
        service.handleClientStatus(
                status.getSessionId(),
                status.getStatus(),
                status.getTrackSid(),
                status.getTrackName(),
                status.getErrorCode(),
                status.getMessage());
    }

    @PostMapping("/intercom/status")
    public void intercomStatus(@RequestBody IntercomStatusMessage status) {
        service.handleIntercomStatus(
                status.getSessionId(),
                status.getStatus(),
                status.getRobotAudioTrackSid(),
                status.getRobotAudioTrackName(),
                status.getErrorCode(),
                status.getMessage());
    }

    @PostMapping("/{sessionId}/token")
    public ViewerTokenResponse token(@PathVariable String sessionId, HttpServletRequest servletRequest) {
        CurrentUser user = currentUserResolver.resolve(servletRequest);
        return service.createViewerToken(sessionId, user);
    }

    @PostMapping("/{sessionId}/intercom/start")
    public IntercomResponse startIntercom(@PathVariable String sessionId, HttpServletRequest servletRequest) {
        return service.startIntercom(sessionId, currentUserResolver.resolve(servletRequest));
    }

    @PostMapping("/{sessionId}/intercom/token")
    public IntercomResponse intercomToken(@PathVariable String sessionId, HttpServletRequest servletRequest) {
        return service.createIntercomToken(sessionId, currentUserResolver.resolve(servletRequest));
    }

    @PostMapping("/{sessionId}/intercom/heartbeat")
    public IntercomResponse intercomHeartbeat(@PathVariable String sessionId, HttpServletRequest servletRequest) {
        return service.heartbeatIntercom(sessionId, currentUserResolver.resolve(servletRequest));
    }

    @PostMapping("/{sessionId}/intercom/stop")
    public VideoSessionResponse stopIntercom(@PathVariable String sessionId, HttpServletRequest servletRequest) {
        return service.stopIntercom(sessionId, currentUserResolver.resolve(servletRequest));
    }

    @PostMapping("/{sessionId}/intercom/expire")
    public java.util.Map<String, Object> expireIntercom(@PathVariable String sessionId) {
        return service.expireIntercom(sessionId);
    }

    @PostMapping("/{sessionId}/intercom/start-command")
    public IntercomStartCommand intercomStartCommand(@PathVariable String sessionId) {
        return service.createIntercomStartCommand(sessionId);
    }

    @PostMapping("/{sessionId}/heartbeat")
    public VideoSessionResponse heartbeat(@PathVariable String sessionId, HttpServletRequest servletRequest) {
        return service.heartbeat(sessionId, currentUserResolver.resolve(servletRequest));
    }

    @PostMapping("/{sessionId}/stop")
    public VideoSessionResponse stop(@PathVariable String sessionId, HttpServletRequest servletRequest) {
        return service.stop(sessionId, currentUserResolver.resolve(servletRequest));
    }

    @PostMapping("/{sessionId}/restart")
    public VideoSessionResponse restart(@PathVariable String sessionId, HttpServletRequest servletRequest) {
        return service.restartSession(sessionId, currentUserResolver.resolve(servletRequest));
    }

    @PostMapping("/{sessionId}/restart-command")
    public VideoStartCommand restartCommand(@PathVariable String sessionId, HttpServletRequest servletRequest) {
        return service.restartSessionCommand(sessionId, currentUserResolver.resolve(servletRequest));
    }

    @PostMapping("/{sessionId}/client-start")
    public VideoStartCommand clientStart(@PathVariable String sessionId, @RequestParam(defaultValue = "video.client.requested") String event) {
        return service.requestClientStart(sessionId, event);
    }

    @PostMapping("/{sessionId}/start-command")
    public VideoStartCommand startCommand(@PathVariable String sessionId) {
        return service.createStartCommand(sessionId);
    }

    @PostMapping("/online-restart-commands")
    public List<VideoStartCommand> onlineRestartCommands(@RequestParam String robotId, @RequestParam String status) {
        return service.handleClientOnline(robotId, status);
    }

    @PostMapping("/{sessionId}/release-idle")
    public java.util.Map<String, Object> releaseIdle(@PathVariable String sessionId) {
        return service.releaseIdleSession(sessionId);
    }

    @PostMapping("/{sessionId}/switch-channel")
    public VideoSessionResponse switchChannel(@PathVariable String sessionId, @Valid @RequestBody SwitchChannelRequest request) {
        return service.switchChannel(sessionId, request);
    }

    @PostMapping("/{sessionId}/snapshots")
    public SnapshotResponse snapshot(
            @PathVariable String sessionId,
            @Valid @RequestBody CreateSnapshotRequest request,
            HttpServletRequest servletRequest) {
        return service.createSnapshot(sessionId, request, currentUserResolver.resolve(servletRequest));
    }

    @PostMapping(value = "/{sessionId}/snapshots/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SnapshotResponse snapshotFile(
            @PathVariable String sessionId,
            @Valid @ModelAttribute CreateSnapshotRequest request,
            @RequestPart("file") MultipartFile file,
            HttpServletRequest servletRequest) {
        return service.createSnapshotFile(sessionId, request, file, currentUserResolver.resolve(servletRequest));
    }

    @GetMapping("/{sessionId}/snapshots")
    public List<SnapshotResponse> snapshots(@PathVariable String sessionId) {
        return snapshotService.recentBySession(sessionId);
    }

    @PostMapping("/{sessionId}/recordings/start")
    public RecordingListItemResponse startRecording(@PathVariable String sessionId, HttpServletRequest servletRequest) {
        return service.startRecording(sessionId, currentUserResolver.resolve(servletRequest));
    }

    @PostMapping("/{sessionId}/recordings/{recordingId}/stop")
    public RecordingListItemResponse stopRecording(
            @PathVariable String sessionId,
            @PathVariable String recordingId,
            HttpServletRequest servletRequest) {
        return service.stopRecording(sessionId, recordingId, currentUserResolver.resolve(servletRequest));
    }

    @GetMapping("/{sessionId}/recordings/active")
    public RecordingListItemResponse activeRecording(@PathVariable String sessionId, HttpServletRequest servletRequest) {
        return service.activeRecording(sessionId, currentUserResolver.resolve(servletRequest));
    }

    @GetMapping("/snapshots")
    public SnapshotListResponse snapshots(
            @RequestParam(required = false) String robotId,
            @RequestParam(required = false) String deviceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return snapshotService.list(robotId, deviceId, page, pageSize);
    }

    @PostMapping("/{sessionId}/_mock/track-published/{trackSid}")
    public VideoSessionResponse mockTrackPublished(@PathVariable String sessionId, @PathVariable String trackSid) {
        return service.markTrackPublished(sessionId, trackSid);
    }
}
