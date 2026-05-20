package com.robot.mediaserver.livekit;

import com.robot.mediaserver.config.MediaProperties;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

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
            log.info("LiveKit room api disabled, skip create room={}", roomName);
            return;
        }
        Map<String, Object> payload = Map.of(
                "name", roomName,
                "emptyTimeout", properties.getLivekit().getRoomEmptyTimeoutSeconds(),
                "departureTimeout", properties.getLivekit().getRoomDepartureTimeoutSeconds());
        post("/twirp/livekit.RoomService/CreateRoom", payload);
        log.info("LiveKit room create requested room={}", roomName);
    }

    /**
     * 删除 LiveKit 房间。
     *
     * @param roomName 房间名
     */
    public void deleteRoom(String roomName) {
        if (!properties.getLivekit().isRoomApiEnabled()) {
            log.info("LiveKit room api disabled, skip delete room={}", roomName);
            return;
        }
        post("/twirp/livekit.RoomService/DeleteRoom", Map.of("room", roomName));
        log.info("LiveKit room delete requested room={}", roomName);
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
}
