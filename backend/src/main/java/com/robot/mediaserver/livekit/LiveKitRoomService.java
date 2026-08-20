package com.robot.mediaserver.livekit;

import com.robot.mediaserver.config.MediaProperties;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * LiveKit 房间管理服务。
 *
 * <p>该服务封装 LiveKit RoomService 管理接口。默认通过配置关闭，便于在没有
 * LiveKit 环境时先完成业务接口和 MQTT 联调。</p>
 *
 * @author leelay
 * @date 2026/05/20
 */
@Service
public class LiveKitRoomService {

    private static final Logger log = LoggerFactory.getLogger(LiveKitRoomService.class);

    private final MediaProperties properties;
    private final LiveKitTokenService tokenService;
    private final RestClient restClient;

    public LiveKitRoomService(
            MediaProperties properties,
            LiveKitTokenService tokenService,
            RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.tokenService = tokenService;
        this.restClient = restClientBuilder.build();
    }

    /**
     * 创建 LiveKit 房间。
     *
     * @param roomName 房间名
     */
    public void createRoom(String roomName) {
        if (!properties.getLivekit().isRoomApiEnabled()) {
            log.info("LiveKit 房间 API 未启用，跳过创建房间 room={}", roomName);
            return;
        }
        Map<String, Object> payload = Map.of(
                "name", roomName,
                "emptyTimeout", properties.getLivekit().getRoomEmptyTimeoutSeconds(),
                "departureTimeout", properties.getLivekit().getRoomDepartureTimeoutSeconds());
        post("/twirp/livekit.RoomService/CreateRoom", payload);
        log.info("已请求创建 LiveKit 房间 room={}", roomName);
    }

    /**
     * 删除 LiveKit 房间。
     *
     * @param roomName 房间名
     */
    public void deleteRoom(String roomName) {
        if (!properties.getLivekit().isRoomApiEnabled()) {
            log.info("LiveKit 房间 API 未启用，跳过删除房间 room={}", roomName);
            return;
        }
        try {
            post("/twirp/livekit.RoomService/DeleteRoom", Map.of("room", roomName));
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND || roomAlreadyAbsent(ex)) {
                log.info("LiveKit 房间已不存在 room={}", roomName);
                return;
            }
            throw ex;
        }
        log.info("已请求删除 LiveKit 房间 room={}", roomName);
    }

    /**
     * 校验房间内是否有活跃的视频推流轨道，用于录像启动前避免在即将关闭的房间上启动录制。
     *
     * @param roomName 房间名
     * @param trackSid 会话关联的视频轨道 SID（可空）
     * @return 房间有活跃视频轨道时返回 {@code true}；房间 API 未启用或查询异常时返回 {@code true}（不阻塞录像）
     */
    public boolean hasActiveVideoTrack(String roomName, String trackSid) {
        if (!properties.getLivekit().isRoomApiEnabled()) {
            return true;
        }
        try {
            Map<?, ?> body = postForObject(
                    "/twirp/livekit.RoomService/ListParticipants", Map.of("room", roomName));
            Object rawParticipants = body == null ? null : body.get("participants");
            if (!(rawParticipants instanceof List<?> participants) || participants.isEmpty()) {
                return false;
            }
            for (Object participant : participants) {
                if (!(participant instanceof Map<?, ?> participantMap)) {
                    continue;
                }
                Object rawTracks = participantMap.get("tracks");
                if (!(rawTracks instanceof List<?> tracks)) {
                    continue;
                }
                for (Object track : tracks) {
                    if (track instanceof Map<?, ?> trackMap && isVideoTrack(trackMap, trackSid)) {
                        return true;
                    }
                }
            }
            return false;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.info("房间不存在，无法校验推流 room={}", roomName);
                return false;
            }
            log.warn("查询房间推流状态失败 room={}，放行录像", roomName, ex);
            return true;
        }
    }

    private boolean isVideoTrack(Map<?, ?> track, String trackSid) {
        Object sid = track.get("sid");
        if (trackSid != null && !trackSid.isBlank() && trackSid.equals(String.valueOf(sid))) {
            return true;
        }
        String type = String.valueOf(track.get("type"));
        String kind = String.valueOf(track.get("kind"));
        String source = String.valueOf(track.get("source"));
        return "0".equals(type)
                || "VIDEO".equalsIgnoreCase(type)
                || "VIDEO".equalsIgnoreCase(kind)
                || "CAMERA".equalsIgnoreCase(source)
                || "SCREEN_SHARE".equalsIgnoreCase(source);
    }

    private Map<?, ?> postForObject(String path, Object payload) {
        String token = tokenService.createAdminToken().token();
        return restClient.post()
                .uri(serverHttpUrl() + path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(payload)
                .retrieve()
                .body(Map.class);
    }

    private void post(String path, Object payload) {
        String token = tokenService.createAdminToken().token();
        restClient.post()
                .uri(serverHttpUrl() + path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(payload)
                .retrieve()
                .toBodilessEntity();
    }

    private boolean roomAlreadyAbsent(RestClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        return body != null && body.contains("could not find object");
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
}
