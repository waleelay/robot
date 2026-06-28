package com.robot.mediaserver.livekit;

import com.robot.mediaserver.config.MediaProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * LiveKit Token 签发服务。
 *
 * <p>平台侧按参与方限制媒体权限：机器人端云接入客户端只允许发布，
 * 前端观看用户仅订阅，交互观看用户额外只允许发布麦克风音频。</p>
 *
 * @author leelay
 * @date 2026/05/19
 */
@Service
public class LiveKitTokenService {

    private final MediaProperties properties;

    public LiveKitTokenService(MediaProperties properties) {
        this.properties = properties;
    }

    /**
     * 生成前端观看 Token。
     *
     * @param roomName LiveKit 房间名
     * @param userId 用户 ID
     * @return Token 和过期时间
     */
    public TokenResult createViewerToken(String roomName, String userId) {
        return createToken(roomName, "user:" + userId + ":web", false, true);
    }

    public TokenResult createViewerToken(String roomName, String userId, String clientId) {
        return createToken(roomName, "user:" + userId + ":" + clientId, false, true);
    }

    /**
     * 生成支持对讲的前端观看 Token，以便观看过程中在现有 Room 直接开启麦克风。
     */
    public TokenResult createInteractiveViewerToken(String roomName, String userId, String clientId) {
        return createToken(roomName, "user:" + userId + ":" + clientId, true, true, List.of("microphone"));
    }

    /**
     * 生成获得讲话权的操作员 Token，可在指定视频 Room 内发布麦克风音频。
     */
    public TokenResult createOperatorToken(String roomName, String userId, String clientId) {
        return createToken(roomName, "operator:" + userId + ":" + clientId, true, true, List.of("microphone"));
    }

    /**
     * 生成机器人端发布 Token。
     *
     * @param roomName LiveKit 房间名
     * @param robotId 机器人 ID
     * @param deviceId 设备 ID
     * @return Token 和过期时间
     */
    public TokenResult createPublisherToken(String roomName, String robotId, String deviceId) {
        return createToken(roomName, "robot:" + robotId + ":" + deviceId, true, false);
    }

    /**
     * 生成机器人对讲 Token：发布现场拾音并订阅操作员语音。
     */
    public TokenResult createRobotIntercomToken(String roomName, String robotId, String deviceId) {
        return createToken(roomName, "robot:" + robotId + ":" + deviceId + ":intercom", true, true, List.of("microphone"));
    }

    /**
     * 生成 LiveKit 管理接口 Token。
     *
     * @return Token 和过期时间
     */
    public TokenResult createAdminToken() {
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC)
                .plusSeconds(properties.getLivekit().getTokenTtlSeconds());
        Map<String, Object> videoGrant = new HashMap<>();
        videoGrant.put("roomCreate", true);
        videoGrant.put("roomList", true);
        videoGrant.put("roomAdmin", true);
        videoGrant.put("roomRecord", true);

        SecretKey key = Keys.hmacShaKeyFor(normalizedSecret().getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .issuer(properties.getLivekit().getApiKey())
                .subject("media-service")
                .expiration(Date.from(expiresAt.toInstant()))
                .claim("video", videoGrant)
                .signWith(key)
                .compact();
        return new TokenResult(token, expiresAt);
    }

    private TokenResult createToken(String roomName, String identity, boolean canPublish, boolean canSubscribe) {
        return createToken(roomName, identity, canPublish, canSubscribe, null);
    }

    private TokenResult createToken(
            String roomName,
            String identity,
            boolean canPublish,
            boolean canSubscribe,
            List<String> publishSources) {
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC)
                .plusSeconds(properties.getLivekit().getTokenTtlSeconds());
        Map<String, Object> videoGrant = new HashMap<>();
        // LiveKit grant 必须限制到指定 Room，不能签发跨 Room 的泛权限 Token。
        videoGrant.put("room", roomName);
        videoGrant.put("roomJoin", true);
        videoGrant.put("canPublish", canPublish);
        videoGrant.put("canSubscribe", canSubscribe);
        if (publishSources != null) {
            videoGrant.put("canPublishSources", publishSources);
        }

        SecretKey key = Keys.hmacShaKeyFor(normalizedSecret().getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .issuer(properties.getLivekit().getApiKey())
                .subject(identity)
                .expiration(Date.from(expiresAt.toInstant()))
                .claim("video", videoGrant)
                .signWith(key)
                .compact();
        return new TokenResult(token, expiresAt);
    }

    private String normalizedSecret() {
        String secret = properties.getLivekit().getApiSecret();
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("LIVEKIT_API_SECRET 至少需要 32 个字符才能用于 HS256 签名");
        }
        return secret;
    }

    public record TokenResult(String token, OffsetDateTime expiresAt) {
    }
}
