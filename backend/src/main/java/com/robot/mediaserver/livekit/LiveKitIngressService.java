package com.robot.mediaserver.livekit;

import com.robot.mediaserver.config.MediaProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * LiveKit Ingress Twirp 管理接口。
 */
@Service
public class LiveKitIngressService {

    private final MediaProperties properties;
    private final LiveKitTokenService tokenService;
    private final RestClient.Builder restClientBuilder;

    public LiveKitIngressService(
            MediaProperties properties,
            LiveKitTokenService tokenService,
            RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.tokenService = tokenService;
        this.restClientBuilder = restClientBuilder;
    }

    public IngressInfo create(CreateIngressRequest request, Duration timeout) {
        requireEnabled();
        Map<String, Object> video = new LinkedHashMap<>();
        video.put("name", "camera");
        video.put("source", "CAMERA");
        video.put("preset", "H264_1080P_30FPS_3_LAYERS");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("inputType", "RTMP_INPUT");
        payload.put("name", request.name());
        payload.put("roomName", request.roomName());
        payload.put("participantIdentity", request.participantIdentity());
        payload.put("participantMetadata", request.participantMetadata());
        payload.put("enableTranscoding", true);
        payload.put("video", video);
        return IngressInfo.from(post("/twirp/livekit.Ingress/CreateIngress", payload, timeout));
    }

    public List<IngressInfo> list(Duration timeout) {
        Map<?, ?> response = post("/twirp/livekit.Ingress/ListIngress", Map.of(), timeout);
        Object items = response.get("items");
        if (!(items instanceof List<?> list)) {
            return List.of();
        }
        List<IngressInfo> result = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof Map<?, ?> ingress) {
                result.add(IngressInfo.from(ingress));
            }
        }
        return List.copyOf(result);
    }

    public IngressInfo delete(String ingressId, Duration timeout) {
        if (ingressId == null || ingressId.isBlank()) {
            throw new IllegalArgumentException("Ingress ID 不能为空");
        }
        return IngressInfo.from(post(
                "/twirp/livekit.Ingress/DeleteIngress",
                Map.of("ingressId", ingressId),
                timeout));
    }

    private Map<?, ?> post(String path, Object payload, Duration timeout) {
        Duration effectiveTimeout = normalizeTimeout(timeout);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(effectiveTimeout);
        requestFactory.setReadTimeout(effectiveTimeout);
        RestClient restClient = restClientBuilder.clone().requestFactory(requestFactory).build();
        String token = tokenService.createIngressAdminToken().token();
        try {
            Map<?, ?> response = restClient.post()
                    .uri(serverHttpUrl() + path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .body(payload)
                    .retrieve()
                    .body(Map.class);
            return response == null ? Map.of() : response;
        } catch (ResourceAccessException exception) {
            throw new IllegalStateException("LiveKit Ingress API 超时或不可达", exception);
        }
    }

    private Duration normalizeTimeout(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("LiveKit Ingress 调用超时必须大于 0");
        }
        Duration configured = Duration.ofMillis(properties.getLivekit().getIngressLivekitCallTimeoutMs());
        return timeout.compareTo(configured) < 0 ? timeout : configured;
    }

    private void requireEnabled() {
        if (!properties.getLivekit().isIngressEnabled()) {
            throw new IllegalStateException("LiveKit ingress 未启用");
        }
    }

    private String serverHttpUrl() {
        String url = properties.getLivekit().getInternalUrl();
        if (url.startsWith("wss://")) {
            return "https://" + url.substring("wss://".length());
        }
        if (url.startsWith("ws://")) {
            return "http://" + url.substring("ws://".length());
        }
        return url;
    }

    private static String value(Map<?, ?> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value != null) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    public record CreateIngressRequest(
            String name,
            String roomName,
            String participantIdentity,
            String participantMetadata) {
    }

    public record IngressInfo(
            String ingressId,
            String name,
            String url,
            String streamKey,
            String roomName,
            String participantIdentity,
            String participantMetadata,
            String status,
            String error) {

        static IngressInfo from(Map<?, ?> source) {
            Map<?, ?> state = source.get("state") instanceof Map<?, ?> map ? map : Map.of();
            return new IngressInfo(
                    value(source, "ingressId", "ingress_id"),
                    value(source, "name"),
                    value(source, "url"),
                    value(source, "streamKey", "stream_key"),
                    value(source, "roomName", "room_name"),
                    value(source, "participantIdentity", "participant_identity"),
                    value(source, "participantMetadata", "participant_metadata"),
                    value(state, "status"),
                    value(state, "error"));
        }
    }
}
