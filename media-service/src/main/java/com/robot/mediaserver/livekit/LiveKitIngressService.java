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

    /**
     * 初始化 LiveKitIngressService，保存所需依赖及初始运行状态。
     *
     * @param properties 服务配置
     * @param tokenService LiveKit Token 签发服务。
     * @param restClientBuilder 下游 HTTP 客户端构建器
     */
    public LiveKitIngressService(
            MediaProperties properties,
            LiveKitTokenService tokenService,
            RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.tokenService = tokenService;
        this.restClientBuilder = restClientBuilder;
    }

    /**
     * 在剩余时间预算内创建 LiveKit Ingress；结果可能包含推流凭据，不得直接记录。
     *
     * @param request 请求参数
     * @param timeout 当前操作允许的最长等待时间
     * @return 新建 Ingress 的标识、地址与发布身份
     */
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

    /**
     * 在给定时间预算内列出 LiveKit Ingress，供运行态对账。
     *
     * @param timeout 当前操作允许的最长等待时间
     * @return 当前可见的 Ingress 资源快照
     */
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

    /**
     * 在给定时间预算内删除指定 Ingress 资源。
     *
     * @param ingressId LiveKit 接入资源标识
     * @param timeout 当前操作允许的最长等待时间
     * @return LiveKit 删除接口返回的 Ingress 信息
     */
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

    /**
     * 创建 Ingress 时指定的房间、发布者身份和元数据。
     *
     * @param name 当前对象的名称
     * @param roomName LiveKit 房间名
     * @param participantIdentity LiveKit 参与者身份
     * @param participantMetadata LiveKit 参与者元数据 JSON
     */
    public record CreateIngressRequest(
            String name,
            String roomName,
            String participantIdentity,
            String participantMetadata) {
    }

    /**
     * LiveKit Ingress 的标识、推流地址、身份和运行状态快照；凭据不得直接写入日志。
     * @param ingressId LiveKit 接入资源标识
     * @param name 当前对象的名称
     * @param url Ingress 推流地址，仅可信管理调用可见
     * @param streamKey LiveKit 管理 API 返回的推流密钥；对外是否返回由上层决定，不得写入日志
     * @param roomName LiveKit 房间名
     * @param participantIdentity LiveKit 参与者身份
     * @param participantMetadata LiveKit 参与者元数据 JSON
     * @param status 当前业务状态，取值遵循所属模型的状态协议
     * @param error LiveKit 上报的 Ingress 错误信息；未提供时为空
     */
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
