package com.robot.mediaserver.livekit;

import com.robot.mediaserver.config.MediaProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/** 调用 LiveKit Egress 启停录制，并返回外部任务标识和状态。 */
@Service
public class LiveKitEgressService {

    private final MediaProperties properties;
    private final LiveKitTokenService tokenService;
    private final RestClient restClient;

    /**
     * 初始化 LiveKitEgressService，保存所需依赖及初始运行状态。
     *
     * @param properties 服务配置
     * @param tokenService LiveKit Token 签发服务。
     * @param restClientBuilder 下游 HTTP 客户端构建器
     */
    public LiveKitEgressService(
            MediaProperties properties,
            LiveKitTokenService tokenService,
            RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.tokenService = tokenService;
        this.restClient = restClientBuilder.build();
    }

    /**
     * 请求 LiveKit 将房间媒体导出为 HLS，返回导出任务而非完成结果。
     *
     * @param roomName LiveKit 房间名
     * @param hlsPrefix HLS 产物在对象存储中的目录前缀
     * @return 导出任务 ID 和初始状态
     */
    public EgressStartResult startRoomHls(String roomName, String hlsPrefix) {
        if (!properties.getLivekit().isEgressEnabled()) {
            throw new IllegalStateException("LiveKit egress 未启用");
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

    /**
     * 请求 LiveKit 将指定轨道导出为 MP4。
     *
     * @param roomName LiveKit 房间名
     * @param trackSid LiveKit 轨道标识
     * @param objectKey 对象在存储桶内的键，不包含访问凭据
     * @return 导出任务 ID 和初始状态
     */
    public EgressStartResult startTrackMp4(String roomName, String trackSid, String objectKey) {
        if (!properties.getLivekit().isEgressEnabled()) {
            throw new IllegalStateException("LiveKit egress 未启用");
        }
        if (trackSid == null || trackSid.isBlank()) {
            throw new IllegalStateException("录像失败：会话无视频轨道");
        }
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("fileType", "MP4");
        output.put("filepath", objectKey);
        output.put("s3", s3Config());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("roomName", roomName);
        payload.put("video_track_id", trackSid);
        payload.put("videoOnly", true);
        payload.put("fileOutputs", java.util.List.of(output));

        Map<?, ?> response = post("/twirp/livekit.Egress/StartTrackCompositeEgress", payload);
        return new EgressStartResult(responseValue(response, "egressId", "egress_id"), responseValue(response, "status"));
    }

    /**
     * 请求停止指定 LiveKit 导出任务；最终产物就绪仍由后续状态确认。
     *
     * @param egressId LiveKit 录像导出任务 ID
     * @return 停止后的导出任务状态
     */
    public EgressStopResult stop(String egressId) {
        if (!properties.getLivekit().isEgressEnabled()) {
            throw new IllegalStateException("LiveKit egress 未启用");
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
            throw new IllegalStateException("LiveKit Egress API 超时或不可达，请检查 LIVEKIT_INTERNAL_URL 和 livekit/egress worker", ex);
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
        String url = properties.getLivekit().getInternalUrl();
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

    /**
     * Egress 启动结果，用于关联录像任务与后续回调。
     *
     * @param egressId LiveKit 录像导出任务 ID
     * @param status 当前业务状态，取值遵循所属模型的状态协议
     */
    public record EgressStartResult(String egressId, String status) {
    }

    /**
     * Egress 停止请求的结果，最终文件就绪仍由后续处理确认。
     *
     * @param egressId LiveKit 录像导出任务 ID
     * @param status 当前业务状态，取值遵循所属模型的状态协议
     */
    public record EgressStopResult(String egressId, String status) {
    }
}
