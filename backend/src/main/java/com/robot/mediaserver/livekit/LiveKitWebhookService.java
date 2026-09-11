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

    public LiveKitWebhookService(
            MediaProperties properties,
            ObjectMapper objectMapper,
            VideoSessionService videoSessionService) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.videoSessionService = videoSessionService;
    }

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
            log.warn("LiveKit Webhook 缺少 Room，交由周期对账补偿 event={} eventId={}",
                    event, text(payload, "id"));
            return;
        }
        log.info("收到 LiveKit 媒体事实事件 event={} eventId={} room={} participant={} trackSid={}",
                event,
                text(payload, "id"),
                roomName,
                text(payload.path("participant"), "identity"),
                text(payload.path("track"), "sid"));
        videoSessionService.reconcileLiveKitRoom(roomName);
    }

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

    public static class InvalidWebhookAuthenticationException extends RuntimeException {
        public InvalidWebhookAuthenticationException(String message) {
            super(message);
        }

        public InvalidWebhookAuthenticationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class InvalidWebhookPayloadException extends RuntimeException {
        public InvalidWebhookPayloadException(String message) {
            super(message);
        }

        public InvalidWebhookPayloadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
