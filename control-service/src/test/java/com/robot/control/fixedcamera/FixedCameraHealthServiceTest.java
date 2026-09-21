package com.robot.control.fixedcamera;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.control.service.ControlVideoCommandService;
import com.robot.control.client.ControlMediaServiceClient;
import com.robot.control.ws.MediaWebSocketPublisher;
import com.robot.media.common.video.FixedCameraIngressResponse;
import com.robot.media.common.video.VideoPublisherMode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FixedCameraHealthServiceTest {

    @Test
    void usesMediaStatusForRtmpWithoutGatewayHealth() {
        ControlMediaServiceClient mediaClient = mock(ControlMediaServiceClient.class);
        FixedCameraHealthService service = new FixedCameraHealthService(
                new ObjectMapper(), mock(MediaWebSocketPublisher.class), mock(ControlVideoCommandService.class));
        service.setMediaServiceClient(mediaClient);
        when(mediaClient.fixedCameraIngressStatuses(List.of("camera-rtmp"))).thenReturn(List.of(
                new FixedCameraIngressResponse(
                        "camera-rtmp", "ingress-1", VideoPublisherMode.LIVEKIT_INGRESS,
                        3, 5, true, "media.fixed.camera-rtmp.visible.main", "fixed-camera:camera-rtmp",
                        "ONLINE", null, OffsetDateTime.now(), false, null, null)));

        Map<String, Object> snapshot = service.authorizedSnapshot(
                List.of(Map.of("cameraId", "camera-rtmp", "protocolType", "RTMP")), "gateway-001");

        Map<?, ?> record = (Map<?, ?>) ((List<?>) snapshot.get("records")).get(0);
        assertThat(record.get("gatewayId")).isNull();
        assertThat(record.get("configReady")).isEqualTo(true);
        assertThat(((Map<?, ?>) record.get("gatewayHealth")).get("reasonCode")).isEqualTo("NOT_APPLICABLE");
        assertThat(((Map<?, ?>) record.get("streamHealth")).get("status")).isEqualTo("AVAILABLE");
    }

    @Test
    void combinesAuthorizedCameraWithGatewayAndStreamHealth() {
        MediaWebSocketPublisher publisher = mock(MediaWebSocketPublisher.class);
        FixedCameraHealthService service = new FixedCameraHealthService(
                new ObjectMapper(), publisher, mock(ControlVideoCommandService.class));
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
        FixedCameraHealthService service = new FixedCameraHealthService(
                new ObjectMapper(), publisher, mock(ControlVideoCommandService.class));

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
                new ObjectMapper(), mock(MediaWebSocketPublisher.class), mock(ControlVideoCommandService.class));
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

    @Test
    void recoversFixedSourcesOnlyAfterRealHealthTransition() {
        ControlVideoCommandService commandService = mock(ControlVideoCommandService.class);
        FixedCameraHealthService service = new FixedCameraHealthService(
                new ObjectMapper(), mock(MediaWebSocketPublisher.class), commandService);
        Instant observedAt = Instant.now();

        service.handleGatewayStatus("gateway/fixed-camera/gateway-001/status", json("""
                {"gatewayId":"gateway-001","status":"OFFLINE","sequence":1,"reportedAt":"%s"}
                """.formatted(observedAt)));
        service.handleGatewayStatus("gateway/fixed-camera/gateway-001/status", json("""
                {"gatewayId":"gateway-001","status":"ONLINE","sequence":2,"reportedAt":"%s"}
                """.formatted(observedAt.plusSeconds(1))));
        service.handleCameraStatus("gateway/fixed-camera/gateway-001/camera/camera-001/status", json("""
                {"gatewayId":"gateway-001","cameraId":"camera-001","health":"UNAVAILABLE",
                 "sequence":3,"checkedAt":"%s"}
                """.formatted(observedAt)));
        service.handleCameraStatus("gateway/fixed-camera/gateway-001/camera/camera-001/status", json("""
                {"gatewayId":"gateway-001","cameraId":"camera-001","health":"AVAILABLE",
                 "sequence":4,"checkedAt":"%s"}
                """.formatted(observedAt.plusSeconds(1))));

        service.recoverPendingSources();

        verify(commandService).recoverFixedCameraSources(null, true);
        // Gateway 恢复覆盖当前单 Gateway 的全部固定摄像头，不再对同一轮 RTSP 状态重复重启。
        org.mockito.Mockito.verify(commandService, org.mockito.Mockito.never())
                .recoverFixedCameraSources("camera-001", false);
    }

    @Test
    void initialHealthySnapshotDoesNotRestartFixedCamera() {
        ControlVideoCommandService commandService = mock(ControlVideoCommandService.class);
        FixedCameraHealthService service = new FixedCameraHealthService(
                new ObjectMapper(), mock(MediaWebSocketPublisher.class), commandService);
        Instant observedAt = Instant.now();

        service.handleGatewayStatus("gateway/fixed-camera/gateway-001/status", json("""
                {"gatewayId":"gateway-001","status":"ONLINE","sequence":1,"reportedAt":"%s"}
                """.formatted(observedAt)));
        service.handleCameraStatus("gateway/fixed-camera/gateway-001/camera/camera-001/status", json("""
                {"gatewayId":"gateway-001","cameraId":"camera-001","health":"AVAILABLE",
                 "sequence":2,"checkedAt":"%s"}
                """.formatted(observedAt)));

        service.recoverPendingSources();

        verifyNoInteractions(commandService);
    }

    @Test
    void recoversOnlyChangedCameraWhenRtspBecomesAvailable() {
        ControlVideoCommandService commandService = mock(ControlVideoCommandService.class);
        FixedCameraHealthService service = new FixedCameraHealthService(
                new ObjectMapper(), mock(MediaWebSocketPublisher.class), commandService);
        Instant observedAt = Instant.now();
        service.handleCameraStatus("gateway/fixed-camera/gateway-001/camera/camera-001/status", json("""
                {"gatewayId":"gateway-001","cameraId":"camera-001","health":"UNAVAILABLE",
                 "sequence":1,"checkedAt":"%s"}
                """.formatted(observedAt)));
        service.handleCameraStatus("gateway/fixed-camera/gateway-001/camera/camera-001/status", json("""
                {"gatewayId":"gateway-001","cameraId":"camera-001","health":"AVAILABLE",
                 "sequence":2,"checkedAt":"%s"}
                """.formatted(observedAt.plusSeconds(1))));

        service.recoverPendingSources();

        verify(commandService).recoverFixedCameraSources("camera-001", false);
    }

    private byte[] json(String value) {
        return value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
