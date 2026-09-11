package com.robot.mediaserver.livekit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.mediaserver.config.MediaProperties;
import com.robot.mediaserver.livekit.LiveKitWebhookService.InvalidWebhookAuthenticationException;
import com.robot.mediaserver.video.service.VideoSessionService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LiveKitWebhookServiceTest {

    private static final String API_KEY = "devkey";
    private static final String API_SECRET = "dev-secret-dev-secret-dev-secret-32";

    private final VideoSessionService videoSessionService = mock(VideoSessionService.class);
    private LiveKitWebhookService service;

    @BeforeEach
    void setUp() {
        MediaProperties properties = new MediaProperties();
        properties.getLivekit().setApiKey(API_KEY);
        properties.getLivekit().setApiSecret(API_SECRET);
        service = new LiveKitWebhookService(properties, new ObjectMapper(), videoSessionService);
    }

    @Test
    void validatesWebhookAndReconcilesRoom() throws Exception {
        byte[] body = """
                {"event":"track_published","id":"EV_1","room":{"name":"media.robot-001.camera01.visible.sub"},"participant":{"identity":"robot:robot-001:camera01"},"track":{"sid":"TR_1"}}
                """.trim().getBytes(StandardCharsets.UTF_8);

        service.receive(body, "Bearer " + token(body));

        verify(videoSessionService).reconcileLiveKitRoom("media.robot-001.camera01.visible.sub");
    }

    @Test
    void rejectsBodyThatDoesNotMatchSignedDigest() throws Exception {
        byte[] signedBody = "{\"event\":\"room_finished\"}".getBytes(StandardCharsets.UTF_8);
        byte[] changedBody = "{\"event\":\"track_published\"}".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> service.receive(changedBody, "Bearer " + token(signedBody)))
                .isInstanceOf(InvalidWebhookAuthenticationException.class);
        verifyNoInteractions(videoSessionService);
    }

    @Test
    void ignoresEventsThatDoNotChangeRoomMediaFacts() throws Exception {
        byte[] body = "{\"event\":\"egress_updated\",\"id\":\"EV_2\"}"
                .getBytes(StandardCharsets.UTF_8);

        service.receive(body, token(body));

        verifyNoInteractions(videoSessionService);
    }

    private String token(byte[] body) throws Exception {
        String digest = Base64.getEncoder().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(body));
        return Jwts.builder()
                .issuer(API_KEY)
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .claim("sha256", digest)
                .signWith(Keys.hmacShaKeyFor(API_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
