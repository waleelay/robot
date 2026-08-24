package com.robot.control.fixedcamera;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.control.config.ControlServiceProperties;
import com.robot.control.config.DateTimeConfig;
import com.robot.control.messaging.RobotMediaCommandService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

class FixedCameraCatalogLeaseServiceTest {

    @Test
    void mergesLeaseAndPublishesSanitizedCatalog() {
        ControlServiceProperties properties = new ControlServiceProperties();
        properties.getMqtt().setFixedCameraGatewayId("gateway-001");
        RobotMediaCommandService commandService = mock(RobotMediaCommandService.class);
        FixedCameraCatalogLeaseService service = new FixedCameraCatalogLeaseService(properties, commandService);
        Instant now = Instant.now();

        service.upsert(new FixedCameraCatalogLeaseRequest(
                "lease-001",
                1L,
                now.minusSeconds(1),
                now.plusSeconds(120),
                List.of(new FixedCameraCatalogLeaseRequest.CameraRecord(
                        "camera-001", true, "RTSP", "rtsp://camera/main", null))));

        ArgumentCaptor<FixedCameraCatalogSnapshot> captor = ArgumentCaptor.forClass(FixedCameraCatalogSnapshot.class);
        verify(commandService).sendFixedCameraCatalog(captor.capture());
        FixedCameraCatalogSnapshot snapshot = captor.getValue();
        assertThat(snapshot.gatewayId()).isEqualTo("gateway-001");
        assertThat(snapshot.cameras()).singleElement().satisfies(camera -> {
            assertThat(camera.cameraId()).isEqualTo("camera-001");
            assertThat(camera.expiresAt()).isAfter(now);
        });

        service.expireLeases(now.plusSeconds(121));
        ArgumentCaptor<FixedCameraCatalogSnapshot> emptyCaptor =
                ArgumentCaptor.forClass(FixedCameraCatalogSnapshot.class);
        verify(commandService, times(2)).sendFixedCameraCatalog(emptyCaptor.capture());
        assertThat(emptyCaptor.getAllValues().get(1).cameras()).isEmpty();
    }

    @Test
    void serializesCatalogSnapshotTimesAsRfc3339ForGateway() throws Exception {
        // 复用生产同款 ObjectMapper（含 DateTimeConfig 全局 yyyy-MM-dd HH:mm:ss 定制器），
        // 验证快照时间字段仍以 RFC3339 序列化，Go 网关才能解析。
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new DateTimeConfig().dateTimeFormatCustomizer().customize(builder);
        ObjectMapper mapper = builder.build();

        Instant issuedAt = Instant.parse("2026-08-24T07:10:05.123456Z");
        FixedCameraCatalogSnapshot snapshot = new FixedCameraCatalogSnapshot(
                "v1",
                "gateway-001",
                1L,
                issuedAt,
                List.of(new FixedCameraCatalogSnapshot.CameraRecord(
                        "camera-001", true, "RTSP", "rtsp://camera/main", null,
                        issuedAt.plusSeconds(120))));

        JsonNode json = mapper.readTree(mapper.writeValueAsString(snapshot));

        assertThat(Instant.parse(json.get("issuedAt").asText())).isEqualTo(issuedAt);
        assertThat(Instant.parse(json.get("cameras").get(0).get("expiresAt").asText()))
                .isEqualTo(issuedAt.plusSeconds(120));
        assertThat(json.get("issuedAt").asText()).doesNotContain(" ");
    }
}
