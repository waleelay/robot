package com.robot.mediaserver.video.api;

import com.robot.mediaserver.auth.CurrentUser;
import com.robot.mediaserver.auth.CurrentUserResolver;
import com.robot.mediaserver.video.dto.CreateSnapshotRequest;
import com.robot.mediaserver.video.dto.CreateVideoSessionRequest;
import com.robot.mediaserver.video.dto.MediaEventLogResponse;
import com.robot.mediaserver.video.dto.MediaTrackResponse;
import com.robot.mediaserver.video.dto.SnapshotResponse;
import com.robot.mediaserver.video.dto.SwitchChannelRequest;
import com.robot.mediaserver.video.dto.VideoSessionResponse;
import com.robot.mediaserver.video.dto.ViewerTokenResponse;
import com.robot.mediaserver.video.event.MediaEventLogService;
import com.robot.mediaserver.video.service.SnapshotService;
import com.robot.mediaserver.video.service.MediaTrackService;
import com.robot.mediaserver.video.service.VideoSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/media/video-sessions")
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

    @GetMapping
    public List<VideoSessionResponse> recent(HttpServletRequest servletRequest) {
        return service.recent(currentUserResolver.resolve(servletRequest));
    }

    @GetMapping("/active")
    public List<VideoSessionResponse> active() {
        return service.active();
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

    @PostMapping("/{sessionId}/token")
    public ViewerTokenResponse token(@PathVariable String sessionId, HttpServletRequest servletRequest) {
        CurrentUser user = currentUserResolver.resolve(servletRequest);
        return service.createViewerToken(sessionId, user);
    }

    @PostMapping("/{sessionId}/stop")
    public VideoSessionResponse stop(@PathVariable String sessionId, HttpServletRequest servletRequest) {
        return service.stop(sessionId, currentUserResolver.resolve(servletRequest));
    }

    @PostMapping("/{sessionId}/restart")
    public VideoSessionResponse restart(@PathVariable String sessionId, HttpServletRequest servletRequest) {
        return service.restartSession(sessionId, currentUserResolver.resolve(servletRequest));
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

    @GetMapping("/{sessionId}/snapshots")
    public List<SnapshotResponse> snapshots(@PathVariable String sessionId) {
        return snapshotService.recentBySession(sessionId);
    }

    @PostMapping("/{sessionId}/_mock/client-acked")
    public VideoSessionResponse mockClientAcked(@PathVariable String sessionId) {
        return service.markClientAcked(sessionId);
    }

    @PostMapping("/{sessionId}/_mock/track-published/{trackSid}")
    public VideoSessionResponse mockTrackPublished(@PathVariable String sessionId, @PathVariable String trackSid) {
        return service.markTrackPublished(sessionId, trackSid);
    }
}
