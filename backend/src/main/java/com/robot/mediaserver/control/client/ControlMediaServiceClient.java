package com.robot.mediaserver.control.client;

import com.robot.mediaserver.auth.CurrentUser;
import com.robot.mediaserver.config.ControlProperties;
import com.robot.mediaserver.robot.dto.RobotDeviceResponse;
import com.robot.mediaserver.video.dto.CreateSnapshotRequest;
import com.robot.mediaserver.video.dto.CreateVideoSessionRequest;
import com.robot.mediaserver.video.dto.MediaEventLogResponse;
import com.robot.mediaserver.video.dto.MediaTrackResponse;
import com.robot.mediaserver.video.dto.SnapshotResponse;
import com.robot.mediaserver.video.dto.SwitchChannelRequest;
import com.robot.mediaserver.video.dto.VideoSessionResponse;
import com.robot.mediaserver.video.dto.ViewerTokenResponse;
import com.robot.mediaserver.video.messaging.VideoStartCommand;
import com.robot.mediaserver.video.messaging.VideoStatusMessage;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ControlMediaServiceClient {

    private final RestClient restClient;

    public ControlMediaServiceClient(ControlProperties properties, RestClient.Builder builder) {
        this.restClient = builder.baseUrl(properties.getMediaServiceBaseUrl()).build();
    }

    public List<RobotDeviceResponse> robots() {
        return getList("/internal/media/robots", new ParameterizedTypeReference<>() {});
    }

    public VideoSessionResponse createVideoSession(CreateVideoSessionRequest request, CurrentUser user) {
        return post("/internal/media/video-sessions", request, user, VideoSessionResponse.class);
    }

    public List<VideoSessionResponse> recent(CurrentUser user) {
        return getList("/internal/media/video-sessions", user, new ParameterizedTypeReference<>() {});
    }

    public List<VideoSessionResponse> active() {
        return getList("/internal/media/video-sessions/active", new ParameterizedTypeReference<>() {});
    }

    public List<String> interruptedRestartCandidates(OffsetDateTime before) {
        return getList(
                "/internal/media/video-sessions/interrupted-restart-candidates?before={before}",
                new ParameterizedTypeReference<>() {},
                before);
    }

    public List<String> idleReleaseCandidates(OffsetDateTime before) {
        return getList(
                "/internal/media/video-sessions/idle-release-candidates?before={before}",
                new ParameterizedTypeReference<>() {},
                before);
    }

    public VideoSessionResponse get(String sessionId, CurrentUser user) {
        return get("/internal/media/video-sessions/{sessionId}", user, VideoSessionResponse.class, sessionId);
    }

    public List<MediaEventLogResponse> events(String sessionId) {
        return getList("/internal/media/video-sessions/{sessionId}/events", new ParameterizedTypeReference<>() {}, sessionId);
    }

    public List<MediaTrackResponse> tracks(String sessionId) {
        return getList("/internal/media/video-sessions/{sessionId}/tracks", new ParameterizedTypeReference<>() {}, sessionId);
    }

    public ViewerTokenResponse token(String sessionId, CurrentUser user) {
        return post("/internal/media/video-sessions/{sessionId}/token", null, user, ViewerTokenResponse.class, sessionId);
    }

    public VideoSessionResponse heartbeat(String sessionId, CurrentUser user) {
        return post("/internal/media/video-sessions/{sessionId}/heartbeat", null, user, VideoSessionResponse.class, sessionId);
    }

    public VideoSessionResponse stop(String sessionId, CurrentUser user) {
        return post("/internal/media/video-sessions/{sessionId}/stop", null, user, VideoSessionResponse.class, sessionId);
    }

    public VideoSessionResponse switchChannel(String sessionId, SwitchChannelRequest request) {
        return post("/internal/media/video-sessions/{sessionId}/switch-channel", request, null, VideoSessionResponse.class, sessionId);
    }

    public SnapshotResponse snapshot(String sessionId, CreateSnapshotRequest request, CurrentUser user) {
        return post("/internal/media/video-sessions/{sessionId}/snapshots", request, user, SnapshotResponse.class, sessionId);
    }

    public List<SnapshotResponse> snapshots(String sessionId) {
        return getList("/internal/media/video-sessions/{sessionId}/snapshots", new ParameterizedTypeReference<>() {}, sessionId);
    }

    public VideoStartCommand requestClientStart(String sessionId, String event) {
        return post("/internal/media/video-sessions/{sessionId}/client-start?event={event}", null, null, VideoStartCommand.class, sessionId, event);
    }

    public VideoStartCommand restartCommand(String sessionId, CurrentUser user) {
        return post("/internal/media/video-sessions/{sessionId}/restart-command", null, user, VideoStartCommand.class, sessionId);
    }

    public VideoStartCommand currentStartCommand(String sessionId) {
        return post("/internal/media/video-sessions/{sessionId}/start-command", null, null, VideoStartCommand.class, sessionId);
    }

    public List<VideoStartCommand> onlineRestartCommands(String robotId, String status) {
        return post(
                "/internal/media/video-sessions/online-restart-commands?robotId={robotId}&status={status}",
                null,
                null,
                new ParameterizedTypeReference<>() {},
                robotId,
                status);
    }

    public void updateVideoStatus(VideoStatusMessage status) {
        post("/internal/media/video-sessions/status", status, null, Void.class);
    }

    public boolean updateRobotClientStatus(Map<String, Object> status) {
        Boolean result = post("/internal/media/robots/client-status", status, null, Boolean.class);
        return Boolean.TRUE.equals(result);
    }

    public Map<String, Object> releaseIdle(String sessionId) {
        return post("/internal/media/video-sessions/{sessionId}/release-idle", null, null, new ParameterizedTypeReference<>() {}, sessionId);
    }

    private <T> T get(String uri, CurrentUser user, Class<T> responseType, Object... uriVariables) {
        return withHeaders(restClient.get().uri(uri, uriVariables), user)
                .retrieve()
                .body(responseType);
    }

    private <T> List<T> getList(String uri, ParameterizedTypeReference<List<T>> responseType, Object... uriVariables) {
        return restClient.get().uri(uri, uriVariables).retrieve().body(responseType);
    }

    private <T> List<T> getList(String uri, CurrentUser user, ParameterizedTypeReference<List<T>> responseType, Object... uriVariables) {
        return withHeaders(restClient.get().uri(uri, uriVariables), user)
                .retrieve()
                .body(responseType);
    }

    private <T> T post(String uri, Object body, CurrentUser user, Class<T> responseType, Object... uriVariables) {
        RestClient.RequestBodySpec spec = withHeaders(restClient.post().uri(uri, uriVariables), user);
        return (body == null ? spec : spec.body(body)).retrieve().body(responseType);
    }

    private <T> T post(String uri, Object body, CurrentUser user, ParameterizedTypeReference<T> responseType, Object... uriVariables) {
        RestClient.RequestBodySpec spec = withHeaders(restClient.post().uri(uri, uriVariables), user);
        return (body == null ? spec : spec.body(body)).retrieve().body(responseType);
    }

    private RestClient.RequestBodySpec withHeaders(RestClient.RequestBodySpec spec, CurrentUser user) {
        if (user == null) {
            return spec;
        }
        return spec
                .header("X-User-Id", user.userId())
                .header("X-Org-Id", user.orgId())
                .header("X-Roles", String.join(",", user.roles()))
                .header("X-Client-Id", user.clientId());
    }

    private RestClient.RequestHeadersSpec<?> withHeaders(RestClient.RequestHeadersSpec<?> spec, CurrentUser user) {
        if (user == null) {
            return spec;
        }
        return spec
                .header("X-User-Id", user.userId())
                .header("X-Org-Id", user.orgId())
                .header("X-Roles", String.join(",", user.roles()))
                .header("X-Client-Id", user.clientId());
    }
}
