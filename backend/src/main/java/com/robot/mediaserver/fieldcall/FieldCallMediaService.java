package com.robot.mediaserver.fieldcall;

import com.robot.media.common.video.CreateFieldCallRequest;
import com.robot.media.common.video.FieldCallResponse;
import com.robot.mediaserver.config.MediaProperties;
import com.robot.mediaserver.livekit.LiveKitRoomService;
import com.robot.mediaserver.livekit.LiveKitTokenService;
import com.robot.mediaserver.livekit.LiveKitTokenService.TokenResult;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 现场应用 视频呼叫：创建隔离 Room 并为双方签发 LiveKit Token。
 */
@Service
public class FieldCallMediaService {

    private final LiveKitTokenService tokenService;
    private final LiveKitRoomService roomService;
    private final MediaProperties properties;

    /**
     * 初始化 FieldCallMediaService，保存所需依赖及初始运行状态。
     *
     * @param tokenService LiveKit Token 签发服务。
     * @param roomService LiveKit 房间管理服务。
     * @param properties 服务配置
     */
    public FieldCallMediaService(
            LiveKitTokenService tokenService,
            LiveKitRoomService roomService,
            MediaProperties properties) {
        this.tokenService = tokenService;
        this.roomService = roomService;
        this.properties = properties;
    }

    /**
     * 创建隔离的现场呼叫房间，并分别签发 App 与中台用户令牌。
     *
     * @param request 请求参数
     * @return 双方接入信息和较早的令牌到期时间
     */
    public FieldCallResponse create(CreateFieldCallRequest request) {
        requireText(request.callId(), "callId");
        requireText(request.appUserId(), "appUserId");
        requireText(request.centerUserId(), "centerUserId");
        String orgId = StringUtils.hasText(request.orgId()) ? request.orgId() : "default";
        String clientId = StringUtils.hasText(request.centerClientId())
                ? request.centerClientId()
                : "web";
        String callIdShort = request.callId().replace("-", "");
        if (callIdShort.length() > 12) {
            callIdShort = callIdShort.substring(0, 12);
        }
        String roomName = "field." + sanitize(orgId) + "." + callIdShort;
        roomService.createRoom(roomName);
        TokenResult appToken = tokenService.createFieldAppToken(roomName, request.appUserId());
        TokenResult centerToken = tokenService.createFieldCenterToken(
                roomName, request.centerUserId(), clientId);
        OffsetDateTime expiresAt = appToken.expiresAt().isBefore(centerToken.expiresAt())
                ? appToken.expiresAt()
                : centerToken.expiresAt();
        return new FieldCallResponse(
                request.callId(),
                roomName,
                properties.getLivekit().getUrl(),
                appToken.token(),
                centerToken.token(),
                expiresAt);
    }

    private static void requireText(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(name + " is required");
        }
    }

    private static String sanitize(String value) {
        return value.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}
