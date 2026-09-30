package com.robot.mediaserver.livekit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.mediaserver.config.MediaProperties;
import com.robot.mediaserver.video.service.VideoSessionService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Set;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 校验并消费 LiveKit Webhook；具体状态变更始终通过 Room API 当前事实完成。
 */
@Service
public class LiveKitWebhookService {

    private static final Logger log = LoggerFactory.getLogger(LiveKitWebhookService.class);
    private static final int MAX_BODY_BYTES = 1024 * 1024;
    /**
     * 允许触发 Room API 事实核验的事件集合，不直接按回调载荷修改媒体状态。
     */
    private static final Set<String> RECONCILE_EVENTS = Set.of(
            "participant_joined",
            "participant_left",
            "participant_connection_aborted",
            "track_published",
            "track_unpublished",
            "room_finished");

    private final MediaProperties properties;
    private final ObjectMapper objectMapper;
    private final VideoSessionService videoSessionService;

    /**
     * 初始化 LiveKitWebhookService，保存所需依赖及初始运行状态。
     *
     * @param properties 服务配置
     * @param objectMapper JSON 编解码器
     * @param videoSessionService 实时视频会话编排服务。
     */
    public LiveKitWebhookService(
            MediaProperties properties,
            ObjectMapper objectMapper,
            VideoSessionService videoSessionService) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.videoSessionService = videoSessionService;
    }

    /**
     * 验证 LiveKit 回调签名与正文摘要后，仅对支持的房间或轨道事件触发事实核验；其他事件忽略。
     * @param body 请求体
     * @param authorization 调用方提供的 Authorization 头；凭据不得写入日志
     */
    public void receive(byte[] body, String authorization) {
        validate(body, authorization);
        JsonNode payload;
        try {
            payload = objectMapper.readTree(body);
        } catch (Exception exception) {
            throw new InvalidWebhookPayloadException("LiveKit Webhook JSON 无效", exception);
        }
        String event = text(payload, "event");
        if (!RECONCILE_EVENTS.contains(event)) {
            return;
        }
        String roomName = text(payload.path("room"), "name");
        if (roomName == null) {
            log.warn("LiveKit 回调缺少房间信息，交由周期对账补偿 事件={} 事件标识={}",
                    event, text(payload, "id"));
            return;
        }
        log.info("收到 LiveKit 媒体事实事件 事件={} 事件标识={} 房间={} 参与者={} 轨道标识={}",
                event,
                text(payload, "id"),
                roomName,
                text(payload.path("participant"), "identity"),
                text(payload.path("track"), "sid"));
        videoSessionService.reconcileLiveKitRoom(roomName);
    }

    /**
     * 限制正文大小并核对 JWT 签名、签发方和正文摘要；任一校验失败均拒绝进入业务核验。
     */
    private void validate(byte[] body, String authorization) {
        if (body == null || body.length == 0 || body.length > MAX_BODY_BYTES) {
            throw new InvalidWebhookPayloadException("LiveKit Webhook 正文为空或超过限制");
        }
        String token = bearerToken(authorization);
        try {
            SecretKey key = Keys.hmacShaKeyFor(
                    properties.getLivekit().getApiSecret().getBytes(StandardCharsets.UTF_8));
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(properties.getLivekit().getApiKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String encodedHash = claims.get("sha256", String.class);
            if (encodedHash == null || !MessageDigest.isEqual(decodeHash(encodedHash), sha256(body))) {
                throw new InvalidWebhookAuthenticationException("LiveKit Webhook 正文摘要不匹配");
            }
        } catch (InvalidWebhookAuthenticationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new InvalidWebhookAuthenticationException("LiveKit Webhook 签名无效", exception);
        }
    }

    private String bearerToken(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            throw new InvalidWebhookAuthenticationException("LiveKit Webhook 缺少 Authorization");
        }
        return authorization.regionMatches(true, 0, "Bearer ", 0, 7)
                ? authorization.substring(7).trim()
                : authorization.trim();
    }

    private byte[] decodeHash(String value) {
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException ignored) {
            return Base64.getUrlDecoder().decode(value);
        }
    }

    private byte[] sha256(byte[] body) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(body);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text.isBlank() ? null : text;
    }

    /** LiveKit 回调签名或身份校验失败，阻止未认证事件进入业务处理。 */
    public static class InvalidWebhookAuthenticationException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        /**
         * 初始化 InvalidWebhookAuthenticationException，保存所需依赖及初始运行状态。
         *
         * @param message 消息内容
         */
        public InvalidWebhookAuthenticationException(String message) {
            super(message);
        }

        /**
         * 初始化 InvalidWebhookAuthenticationException，保存所需依赖及初始运行状态。
         *
         * @param message 消息内容
         * @param cause 触发当前异常的原始原因
         */
        public InvalidWebhookAuthenticationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** LiveKit 回调正文格式或内容不符合事件解析要求。 */
    public static class InvalidWebhookPayloadException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        /**
         * 初始化 InvalidWebhookPayloadException，保存所需依赖及初始运行状态。
         *
         * @param message 消息内容
         */
        public InvalidWebhookPayloadException(String message) {
            super(message);
        }

        /**
         * 初始化 InvalidWebhookPayloadException，保存所需依赖及初始运行状态。
         *
         * @param message 消息内容
         * @param cause 触发当前异常的原始原因
         */
        public InvalidWebhookPayloadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
