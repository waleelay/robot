package com.robot.mediaserver.livekit;

import com.robot.mediaserver.config.MediaProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Service
public class LiveKitEgressService {

    private final MediaProperties properties;
    private final LiveKitTokenService tokenService;
    private final RestClient restClient;

    public LiveKitEgressService(
            MediaProperties properties,
            LiveKitTokenService tokenService,
            RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.tokenService = tokenService;
        this.restClient = restClientBuilder.build();
    }

    public EgressStartResult startRoomHls(String roomName, String hlsPrefix) {
        if (!properties.getLivekit().isEgressEnabled()) {
            throw new IllegalStateException("LiveKit egress is disabled");
        }
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("protocol", "HLS_PROTOCOL");
        output.put("filenamePrefix", hlsPrefix + "segment");
        output.put("playlistName", hlsPrefix + "master.m3u8");
        output.put("segmentDuration", properties.getLivekit().getEgressSegmentDurationSeconds());
        output.put("s3", s3Config());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("roomName", roomName);
        payload.put("videoOnly", true);
        payload.put("segmentOutputs", java.util.List.of(output));

        Map<?, ?> response = post("/twirp/livekit.Egress/StartRoomCompositeEgress", payload);
        return new EgressStartResult(responseValue(response, "egressId", "egress_id"), responseValue(response, "status"));
    }

    public EgressStartResult startRoomMp4(String roomName, String objectKey) {
        if (!properties.getLivekit().isEgressEnabled()) {
            throw new IllegalStateException("LiveKit egress is disabled");
        }
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("fileType", "MP4");
        output.put("filepath", objectKey);
        output.put("s3", s3Config());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("roomName", roomName);
        payload.put("videoOnly", true);
        payload.put("fileOutputs", java.util.List.of(output));

        Map<?, ?> response = post("/twirp/livekit.Egress/StartRoomCompositeEgress", payload);
        return new EgressStartResult(responseValue(response, "egressId", "egress_id"), responseValue(response, "status"));
    }

    public EgressStopResult stop(String egressId) {
        if (!properties.getLivekit().isEgressEnabled()) {
            throw new IllegalStateException("LiveKit egress is disabled");
        }
        Map<?, ?> response = post("/twirp/livekit.Egress/StopEgress", Map.of("egressId", egressId));
        return new EgressStopResult(responseValue(response, "egressId", "egress_id"), responseValue(response, "status"));
    }

    private Map<?, ?> post(String path, Object payload) {
        String token = tokenService.createAdminToken().token();
        try {
            return restClient.post()
                    .uri(serverHttpUrl() + path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .body(payload)
                    .retrieve()
                    .body(Map.class);
        } catch (ResourceAccessException ex) {
            throw new IllegalStateException("LiveKit Egress API timeout or unreachable, check LIVEKIT_URL and livekit/egress worker", ex);
        }
    }

    private Map<String, Object> s3Config() {
        Map<String, Object> s3 = new LinkedHashMap<>();
        s3.put("accessKey", properties.getMinio().getAccessKey());
        s3.put("secret", properties.getMinio().getSecretKey());
        s3.put("region", properties.getLivekit().getEgressS3Region());
        s3.put("bucket", properties.getMinio().getBucket());
        s3.put("endpoint", properties.getMinio().getEndpoint());
        s3.put("forcePathStyle", properties.getLivekit().isEgressS3ForcePathStyle());
        return s3;
    }

    private String serverHttpUrl() {
        String url = properties.getLivekit().getUrl();
        if (url.startsWith("wss://")) {
            return "https://" + url.substring("wss://".length());
        }
        if (url.startsWith("ws://")) {
            return "http://" + url.substring("ws://".length());
        }
        return url;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String responseValue(Map<?, ?> response, String... keys) {
        for (String key : keys) {
            Object value = response.get(key);
            if (value != null) {
                return stringValue(value);
            }
        }
        return null;
    }

    public record EgressStartResult(String egressId, String status) {
    }

    public record EgressStopResult(String egressId, String status) {
    }
}
