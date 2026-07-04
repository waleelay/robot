package com.robot.mediaserver.control.api;

import com.robot.mediaserver.control.auth.CurrentUser;
import com.robot.mediaserver.control.auth.CurrentUserResolver;
import com.robot.mediaserver.control.client.ControlMediaServiceClient;
import com.robot.mediaserver.control.service.ControlVideoCommandService;
import com.robot.mediaserver.control.dto.FileListItemResponse;
import com.robot.mediaserver.control.dto.MediaEventLogResponse;
import com.robot.mediaserver.control.dto.MediaTrackResponse;
import com.robot.mediaserver.control.dto.SwitchChannelRequest;
import com.robot.mediaserver.control.dto.VideoSessionResponse;
import com.robot.mediaserver.control.dto.ViewerTokenResponse;
import com.robot.mediaserver.control.dto.IntercomResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/control/video-sessions")
public class ControlVideoSessionController {

    private final ControlMediaServiceClient mediaServiceClient;
    private final ControlVideoCommandService controlVideoCommandService;
    private final CurrentUserResolver currentUserResolver;

    public ControlVideoSessionController(
            ControlMediaServiceClient mediaServiceClient,
            ControlVideoCommandService controlVideoCommandService,
            CurrentUserResolver currentUserResolver) {
        this.mediaServiceClient = mediaServiceClient;
        this.controlVideoCommandService = controlVideoCommandService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping
    public List<VideoSessionResponse> recent(HttpServletRequest servletRequest) {
        return mediaServiceClient.recent(currentUserResolver.resolve(servletRequest));
    }

    @GetMapping("/active")
    public List<VideoSessionResponse> active() {
        return mediaServiceClient.active();
    }

    @GetMapping("/{sessionId}")
    public VideoSessionResponse get(@PathVariable String sessionId, HttpServletRequest servletRequest) {
        return mediaServiceClient.get(sessionId, currentUserResolver.resolve(servletRequest));
    }

    @GetMapping("/{sessionId}/events")
    public List<MediaEventLogResponse> events(@PathVariable String sessionId) {
        return mediaServiceClient.events(sessionId);
    }

    @GetMapping("/{sessionId}/tracks")
    public List<MediaTrackResponse> tracks(@PathVariable String sessionId) {
        return mediaServiceClient.tracks(sessionId);
    }

    @PostMapping("/{sessionId}/token")
    public ViewerTokenResponse token(@PathVariable String sessionId, HttpServletRequest servletRequest) {
        CurrentUser user = currentUserResolver.resolve(servletRequest);
        return mediaServiceClient.token(sessionId, user);
    }

    @PostMapping("/{sessionId}/intercom/start")
    public IntercomResponse startIntercom(@PathVariable String sessionId, HttpServletRequest servletRequest) {
        return controlVideoCommandService.startIntercom(sessionId, currentUserResolver.resolve(servletRequest));
    }

    @PostMapping("/{sessionId}/intercom/token")
    public IntercomResponse intercomToken(@PathVariable String sessionId, HttpServletRequest servletRequest) {
        return mediaServiceClient.intercomToken(sessionId, currentUserResolver.resolve(servletRequest));
    }

    @PostMapping("/{sessionId}/intercom/heartbeat")
    public IntercomResponse intercomHeartbeat(@PathVariable String sessionId, HttpServletRequest servletRequest) {
        return mediaServiceClient.intercomHeartbeat(sessionId, currentUserResolver.resolve(servletRequest));
    }

    @PostMapping("/{sessionId}/intercom/stop")
    public VideoSessionResponse stopIntercom(@PathVariable String sessionId, HttpServletRequest servletRequest) {
        return controlVideoCommandService.stopIntercom(sessionId, currentUserResolver.resolve(servletRequest));
    }

    @PostMapping("/{sessionId}/heartbeat")
    public VideoSessionResponse heartbeat(@PathVariable String sessionId, HttpServletRequest servletRequest) {
        return mediaServiceClient.heartbeat(sessionId, currentUserResolver.resolve(servletRequest));
    }

    @PostMapping("/{sessionId}/stop")
    public VideoSessionResponse stop(@PathVariable String sessionId, HttpServletRequest servletRequest) {
        return mediaServiceClient.stop(sessionId, currentUserResolver.resolve(servletRequest));
    }

    @PostMapping("/{sessionId}/restart")
    public VideoSessionResponse restart(@PathVariable String sessionId, HttpServletRequest servletRequest) {
        return controlVideoCommandService.restartVideo(sessionId, currentUserResolver.resolve(servletRequest));
    }

    @PostMapping("/{sessionId}/switch-channel")
    public VideoSessionResponse switchChannel(@PathVariable String sessionId, @Valid @RequestBody SwitchChannelRequest request) {
        return controlVideoCommandService.switchChannel(sessionId, request);
    }

    @PostMapping("/{sessionId}/recordings/start")
    public FileListItemResponse startRecording(@PathVariable String sessionId, HttpServletRequest servletRequest) {
        return mediaServiceClient.startLiveRecording(sessionId, currentUserResolver.resolve(servletRequest));
    }

    @PostMapping("/{sessionId}/recordings/{fileId}/stop")
    public FileListItemResponse stopRecording(
            @PathVariable String sessionId,
            @PathVariable String fileId,
            HttpServletRequest servletRequest) {
        return mediaServiceClient.stopLiveRecording(sessionId, fileId, currentUserResolver.resolve(servletRequest));
    }

    @GetMapping("/{sessionId}/recordings/active")
    public FileListItemResponse activeRecording(@PathVariable String sessionId, HttpServletRequest servletRequest) {
        return mediaServiceClient.activeLiveRecording(sessionId, currentUserResolver.resolve(servletRequest));
    }
}
