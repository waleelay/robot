package com.robot.mediaserver.control.client;

import com.robot.mediaserver.auth.CurrentUser;
import com.robot.mediaserver.config.ControlProperties;
import com.robot.mediaserver.robot.dto.RobotDeviceResponse;
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
import com.robot.mediaserver.video.messaging.VideoStartCommand;
import com.robot.mediaserver.video.messaging.VideoStatusMessage;
import com.robot.mediaserver.video.messaging.IntercomStartCommand;
import com.robot.mediaserver.video.messaging.IntercomStatusMessage;
import com.robot.mediaserver.recording.dto.PlaybackUrlResponse;
import com.robot.mediaserver.recording.dto.RecordingListItemResponse;
import com.robot.mediaserver.recording.dto.RecordingListResponse;
import com.robot.mediaserver.recording.model.RecordingStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

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

    public IntercomResponse createIntercom(CreateVideoSessionRequest request, CurrentUser user) {
        return post("/internal/media/video-sessions/intercom", request, user, IntercomResponse.class);
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

    public List<String> intercomTimeoutCandidates(OffsetDateTime before) {
        return getList(
                "/internal/media/video-sessions/intercom-timeout-candidates?before={before}",
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

    public IntercomResponse startIntercom(String sessionId, CurrentUser user) {
        return post("/internal/media/video-sessions/{sessionId}/intercom/start", null, user, IntercomResponse.class, sessionId);
    }

    public IntercomResponse intercomToken(String sessionId, CurrentUser user) {
        return post("/internal/media/video-sessions/{sessionId}/intercom/token", null, user, IntercomResponse.class, sessionId);
    }

    public IntercomResponse intercomHeartbeat(String sessionId, CurrentUser user) {
        return post("/internal/media/video-sessions/{sessionId}/intercom/heartbeat", null, user, IntercomResponse.class, sessionId);
    }

    public VideoSessionResponse stopIntercom(String sessionId, CurrentUser user) {
        return post("/internal/media/video-sessions/{sessionId}/intercom/stop", null, user, VideoSessionResponse.class, sessionId);
    }

    public Map<String, Object> expireIntercom(String sessionId) {
        return post("/internal/media/video-sessions/{sessionId}/intercom/expire", null, null, new ParameterizedTypeReference<>() {}, sessionId);
    }

    public IntercomStartCommand intercomStartCommand(String sessionId) {
        return post("/internal/media/video-sessions/{sessionId}/intercom/start-command", null, null, IntercomStartCommand.class, sessionId);
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

    public SnapshotResponse snapshotFile(String sessionId, CreateSnapshotRequest request, MultipartFile file, CurrentUser user) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        addPart(body, "trackSid", request.getTrackSid());
        addPart(body, "reason", request.getReason());
        addPart(body, "remark", request.getRemark());
        addPart(body, "clientCapturedAt", request.getClientCapturedAt() == null ? null : request.getClientCapturedAt().toString());
        addPart(body, "previewImageHash", request.getPreviewImageHash());
        try {
            body.add("file", new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename() == null ? "snapshot.jpg" : file.getOriginalFilename();
                }
            });
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read snapshot file", ex);
        }
        return withHeaders(restClient.post()
                        .uri("/internal/media/video-sessions/{sessionId}/snapshots/file", sessionId)
                        .contentType(MediaType.MULTIPART_FORM_DATA), user)
                .body(body)
                .retrieve()
                .body(SnapshotResponse.class);
    }

    public RecordingListItemResponse startLiveRecording(String sessionId, CurrentUser user) {
        return post("/internal/media/video-sessions/{sessionId}/recordings/start", null, user, RecordingListItemResponse.class, sessionId);
    }

    public RecordingListItemResponse stopLiveRecording(String sessionId, String recordingId, CurrentUser user) {
        return post("/internal/media/video-sessions/{sessionId}/recordings/{recordingId}/stop",
                null,
                user,
                RecordingListItemResponse.class,
                sessionId,
                recordingId);
    }

    public RecordingListItemResponse activeLiveRecording(String sessionId, CurrentUser user) {
        return get("/internal/media/video-sessions/{sessionId}/recordings/active", user, RecordingListItemResponse.class, sessionId);
    }

    public SnapshotListResponse snapshots(String robotId, String deviceId, int page, int pageSize) {
        String uri = UriComponentsBuilder.fromPath("/internal/media/video-sessions/snapshots")
                .queryParamIfPresent("robotId", optional(robotId))
                .queryParamIfPresent("deviceId", optional(deviceId))
                .queryParam("page", page)
                .queryParam("pageSize", pageSize)
                .build()
                .toUriString();
        return get(uri, null, SnapshotListResponse.class);
    }

    public byte[] snapshotImage(String snapshotId) {
        return restClient.get()
                .uri("/internal/media/snapshots/{snapshotId}/image", snapshotId)
                .retrieve()
                .body(byte[].class);
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

    public void updateIntercomStatus(IntercomStatusMessage status) {
        post("/internal/media/video-sessions/intercom/status", status, null, Void.class);
    }

    public boolean updateRobotClientStatus(Map<String, Object> status) {
        Boolean result = post("/internal/media/robots/client-status", status, null, Boolean.class);
        return Boolean.TRUE.equals(result);
    }

    public Map<String, Object> releaseIdle(String sessionId) {
        return post("/internal/media/video-sessions/{sessionId}/release-idle", null, null, new ParameterizedTypeReference<>() {}, sessionId);
    }

    public RecordingListResponse recordings(
            String robotId,
            String deviceId,
            String sourceType,
            RecordingStatus status,
            OffsetDateTime from,
            OffsetDateTime to,
            int page,
            int size,
            CurrentUser user) {
        String uri = UriComponentsBuilder.fromPath("/internal/media/recordings")
                .queryParamIfPresent("robotId", optional(robotId))
                .queryParamIfPresent("deviceId", optional(deviceId))
                .queryParamIfPresent("sourceType", optional(sourceType))
                .queryParamIfPresent("status", Optional.ofNullable(status).map(Enum::name))
                .queryParamIfPresent("from", Optional.ofNullable(from))
                .queryParamIfPresent("to", Optional.ofNullable(to))
                .queryParam("page", page)
                .queryParam("size", size)
                .build()
                .toUriString();
        return get(uri, user, RecordingListResponse.class);
    }

    public PlaybackUrlResponse recordingPlayUrl(String recordingId, CurrentUser user) {
        return post("/internal/media/recordings/{recordingId}/play-url", null, user, PlaybackUrlResponse.class, recordingId);
    }

    public byte[] recordingHlsAsset(String recordingId, String objectName, String token) {
        return restClient.get()
                .uri("/internal/media/recordings/{recordingId}/hls/{objectName}?token={token}", recordingId, objectName, token)
                .retrieve()
                .body(byte[].class);
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

    private void addPart(MultiValueMap<String, Object> body, String key, Object value) {
        if (value != null) {
            body.add(key, value);
        }
    }

    private Optional<String> optional(String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }
}
