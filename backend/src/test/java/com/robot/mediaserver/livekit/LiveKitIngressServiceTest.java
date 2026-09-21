package com.robot.mediaserver.livekit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class LiveKitIngressServiceTest {

    @Test
    void mapsCamelCaseIngressResponseAndNestedState() {
        var ingress = LiveKitIngressService.IngressInfo.from(Map.of(
                "ingressId", "IN_001",
                "name", "fixed-camera-001",
                "url", "rtmp://example/live",
                "streamKey", "RT_secret",
                "roomName", "media.fixed.001.visible.main",
                "participantIdentity", "fixed-camera:001",
                "participantMetadata", "{\"cameraId\":\"001\"}",
                "state", Map.of("status", "ENDPOINT_WAITING", "error", "")));

        assertThat(ingress.ingressId()).isEqualTo("IN_001");
        assertThat(ingress.streamKey()).isEqualTo("RT_secret");
        assertThat(ingress.status()).isEqualTo("ENDPOINT_WAITING");
        assertThat(ingress.error()).isEmpty();
    }

    @Test
    void mapsSnakeCaseFieldsReturnedByOlderCompatibleServer() {
        var ingress = LiveKitIngressService.IngressInfo.from(Map.of(
                "ingress_id", "IN_002",
                "stream_key", "RT_secret_2",
                "room_name", "media.fixed.002.visible.main",
                "participant_identity", "fixed-camera:002"));

        assertThat(ingress.ingressId()).isEqualTo("IN_002");
        assertThat(ingress.roomName()).isEqualTo("media.fixed.002.visible.main");
        assertThat(ingress.participantIdentity()).isEqualTo("fixed-camera:002");
    }
}
