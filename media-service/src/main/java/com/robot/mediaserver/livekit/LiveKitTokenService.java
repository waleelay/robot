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

    /**
     * 初始化 LiveKitTokenService，保存所需依赖及初始运行状态。
     *
     * @param properties 服务配置
     */
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
     * 为指定观看身份签发限定房间权限的 LiveKit 令牌。
     *
     * @param roomName LiveKit 房间名
     * @param userId 用户 ID
     * @param clientId 客户端 ID
     * @return 令牌及到期时间；令牌不得写入日志
     */
    public TokenResult createViewerToken(String roomName, String userId, String clientId) {
        return createToken(roomName, "user:" + userId + ":" + clientId, false, true);
    }

    /**
     * 生成支持对讲的前端观看 Token，以便观看过程中在现有 Room 直接开启麦克风。
     *
     * @param roomName LiveKit 房间名
     * @param userId 用户 ID
     * @param clientId 客户端 ID
     * @return 可参与交互的观看令牌及到期时间
     */
    public TokenResult createInteractiveViewerToken(String roomName, String userId, String clientId) {
        return createToken(roomName, "user:" + userId + ":" + clientId, true, true, List.of("microphone"));
    }

    /**
     * 生成获得讲话权的操作员 Token，可在指定视频 Room 内发布麦克风音频。
     *
     * @param roomName LiveKit 房间名
     * @param userId 用户 ID
     * @param clientId 客户端 ID
     * @return 限定对讲操作权限的令牌及到期时间
     */
    public TokenResult createOperatorToken(String roomName, String userId, String clientId) {
        return createToken(roomName, "operator:" + userId + ":" + clientId, true, true, List.of("microphone"));
    }

    /**
     * 根据调用方传入的发布端身份生成发布 Token。
     *
     * @param roomName LiveKit 房间名
     * @param publisherIdentity 发布端身份
     * @return Token 和过期时间
     */
    public TokenResult createPublisherToken(String roomName, String publisherIdentity) {
        return createToken(roomName, publisherIdentity, true, false);
    }

    /**
     * 生成机器人对讲 Token：发布现场拾音并订阅操作员语音。
     *
     * @param roomName LiveKit 房间名
     * @param robotId 机器人 ID
     * @param deviceId 设备 ID
     * @return 供机器人发布对讲音频的令牌及到期时间
     */
    public TokenResult createRobotIntercomToken(String roomName, String robotId, String deviceId) {
        return createToken(roomName, "robot:" + robotId + ":" + deviceId + ":intercom", true, true, List.of("microphone"));
    }

    /**
     * 现场应用 Token：发布摄像头与麦克风，并订阅中心端音频。
     *
     * @param roomName LiveKit 房间名
     * @param userId 用户 ID
     * @return 现场应用 的房间接入令牌及到期时间
     */
    public TokenResult createFieldAppToken(String roomName, String userId) {
        return createToken(
                roomName,
                "field-app:" + userId,
                true,
                true,
                List.of("camera", "microphone"),
                properties.getLivekit().getFieldCallTokenTtlSeconds());
    }

    /**
     * 现场呼叫中心端 Token：订阅 App 音视频，并发布麦克风。
     *
     * @param roomName LiveKit 房间名
     * @param userId 用户 ID
     * @param clientId 客户端 ID
     * @return 指挥中心用户的房间接入令牌及到期时间
     */
    public TokenResult createFieldCenterToken(String roomName, String userId, String clientId) {
        return createToken(
                roomName,
                "field-center:" + userId + ":" + clientId,
                true,
                true,
                List.of("microphone"),
                properties.getLivekit().getFieldCallTokenTtlSeconds());
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

    /**
     * 生成只允许管理 LiveKit Ingress 的令牌。
     *
     * @return Token 和过期时间
     */
    public TokenResult createIngressAdminToken() {
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC)
                .plusSeconds(properties.getLivekit().getTokenTtlSeconds());
        Map<String, Object> videoGrant = new HashMap<>();
        videoGrant.put("ingressAdmin", true);

        SecretKey key = Keys.hmacShaKeyFor(normalizedSecret().getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .issuer(properties.getLivekit().getApiKey())
                .subject("media-service-ingress")
                .expiration(Date.from(expiresAt.toInstant()))
                .claim("video", videoGrant)
                .signWith(key)
                .compact();
        return new TokenResult(token, expiresAt);
    }

    /**
     * 生成指定房间的管理令牌。
     *
     * @param roomName 房间名称
     * @return 令牌及过期时间
     */
    public TokenResult createRoomAdminToken(String roomName) {
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC)
                .plusSeconds(properties.getLivekit().getTokenTtlSeconds());
        Map<String, Object> videoGrant = new HashMap<>();
        videoGrant.put("room", roomName);
        videoGrant.put("roomAdmin", true);

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
        return createToken(roomName, identity, canPublish, canSubscribe, null, null);
    }

    private TokenResult createToken(
            String roomName,
            String identity,
            boolean canPublish,
            boolean canSubscribe,
            List<String> publishSources) {
        return createToken(roomName, identity, canPublish, canSubscribe, publishSources, null);
    }

    private TokenResult createToken(
            String roomName,
            String identity,
            boolean canPublish,
            boolean canSubscribe,
            List<String> publishSources,
            Long ttlSeconds) {
        long ttl = ttlSeconds != null && ttlSeconds > 0
                ? ttlSeconds
                : properties.getLivekit().getTokenTtlSeconds();
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(ttl);
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

    /**
     * 媒体访问令牌及有效期，调用方必须按凭据处理令牌内容。
     *
     * @param token 访问令牌
     * @param expiresAt 有效期截止时间
     */
    public record TokenResult(String token, OffsetDateTime expiresAt) {
    }
}
