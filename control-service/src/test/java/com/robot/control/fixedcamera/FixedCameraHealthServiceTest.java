package com.robot.control.fixedcamera;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.control.ws.MediaWebSocketPublisher;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FixedCameraHealthServiceTest {

    @Test
    void combinesAuthorizedCameraWithGatewayAndStreamHealth() {
        MediaWebSocketPublisher publisher = mock(MediaWebSocketPublisher.class);
        FixedCameraHealthService service = new FixedCameraHealthService(new ObjectMapper(), publisher);
        service.handleGatewayStatus("gateway/fixed-camera/gateway-001/status", json("""
                {"gatewayId":"gateway-001","status":"ONLINE","sequence":1,"reportedAt":"%s"}
                """.formatted(Instant.now())));
        service.handleCameraStatus("gateway/fixed-camera/gateway-001/camera/camera-001/status", json("""
                {"gatewayId":"gateway-001","cameraId":"camera-001","health":"AVAILABLE",
                 "sequence":2,"checkedAt":"%s"}
                """.formatted(Instant.now())));

        Map<String, Object> snapshot = service.authorizedSnapshot(
                List.of(Map.of("cameraId", "camera-001")), "gateway-001");

        Map<?, ?> record = (Map<?, ?>) ((List<?>) snapshot.get("records")).get(0);
        assertThat(((Map<?, ?>) record.get("gatewayHealth")).get("status")).isEqualTo("ONLINE");
        assertThat(((Map<?, ?>) record.get("streamHealth")).get("status")).isEqualTo("AVAILABLE");
        verify(publisher).publish("fixed-camera.health.changed", Map.of(
                "scope", "GATEWAY", "gatewayId", "gateway-001", "status", "ONLINE"));
    }

    @Test
    void rejectsTopicPayloadIdentityMismatch() {
        MediaWebSocketPublisher publisher = mock(MediaWebSocketPublisher.class);
        FixedCameraHealthService service = new FixedCameraHealthService(new ObjectMapper(), publisher);

        service.handleCameraStatus("gateway/fixed-camera/gateway-001/camera/camera-001/status", json("""
                {"gatewayId":"gateway-002","cameraId":"camera-001","health":"AVAILABLE"}
                """));

        Map<String, Object> snapshot = service.authorizedSnapshot(
                List.of(Map.of("cameraId", "camera-001")), "gateway-001");
        Map<?, ?> record = (Map<?, ?>) ((List<?>) snapshot.get("records")).get(0);
        assertThat(((Map<?, ?>) record.get("streamHealth")).get("status")).isEqualTo("UNKNOWN");
    }

    @Test
    void expiresGatewayAndCameraStatusesWithoutKeepingOldOnlineState() {
        FixedCameraHealthService service = new FixedCameraHealthService(
                new ObjectMapper(), mock(MediaWebSocketPublisher.class));
        Instant observedAt = Instant.now();
        service.handleGatewayStatus("gateway/fixed-camera/gateway-001/status", json("""
                {"gatewayId":"gateway-001","status":"ONLINE","sequence":1,"reportedAt":"%s"}
                """.formatted(observedAt)));
        service.handleCameraStatus("gateway/fixed-camera/gateway-001/camera/camera-001/status", json("""
                {"gatewayId":"gateway-001","cameraId":"camera-001","health":"AVAILABLE",
                 "sequence":2,"checkedAt":"%s"}
                """.formatted(observedAt)));

        service.expireStaleStates(observedAt.plusSeconds(121));

        Map<String, Object> snapshot = service.authorizedSnapshot(
                List.of(Map.of("cameraId", "camera-001")), "gateway-001");
        Map<?, ?> record = (Map<?, ?>) ((List<?>) snapshot.get("records")).get(0);
        assertThat(((Map<?, ?>) record.get("gatewayHealth")).get("status")).isEqualTo("OFFLINE");
        assertThat(((Map<?, ?>) record.get("streamHealth")).get("status")).isEqualTo("UNKNOWN");
    }

    private byte[] json(String value) {
        return value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
