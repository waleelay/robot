package com.robot.mediaserver.livekit;

import com.robot.mediaserver.config.MediaProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * LiveKit Token 签发服务。
 *
 * <p>平台侧区分发布 Token 和观看 Token：机器人端云接入客户端只允许发布，
 * 前端用户只允许订阅，避免越权发布或订阅其他机器人视频。</p>
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

    private TokenResult createToken(String roomName, String identity, boolean canPublish, boolean canSubscribe) {
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC)
                .plusSeconds(properties.getLivekit().getTokenTtlSeconds());
        Map<String, Object> videoGrant = new HashMap<>();
        // LiveKit grant 必须限制到指定 Room，不能签发跨 Room 的泛权限 Token。
        videoGrant.put("room", roomName);
        videoGrant.put("roomJoin", true);
        videoGrant.put("canPublish", canPublish);
        videoGrant.put("canSubscribe", canSubscribe);

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
            throw new IllegalStateException("LIVEKIT_API_SECRET must be at least 32 characters for HS256 signing");
        }
        return secret;
    }

    public record TokenResult(String token, OffsetDateTime expiresAt) {
    }
}
