package com.robot.mediaserver.livekit;

import static org.assertj.core.api.Assertions.assertThat;

import com.robot.mediaserver.config.MediaProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LiveKitTokenServiceTest {

    @Test
    void createsRoomScopedAdminToken() {
        MediaProperties properties = new MediaProperties();
        properties.getLivekit().setApiKey("devkey");
        properties.getLivekit().setApiSecret("dev-secret-dev-secret-dev-secret-32");
        LiveKitTokenService service = new LiveKitTokenService(properties);

        String token = service.createRoomAdminToken("media.robot-001.camera01.visible").token();
        Map<?, ?> video = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(properties.getLivekit().getApiSecret().getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("video", Map.class);

        assertThat(video.get("roomAdmin")).isEqualTo(true);
        assertThat(video.get("room")).isEqualTo("media.robot-001.camera01.visible");
    }
}
