package com.robot.control.fixedcamera;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.robot.control.config.ControlServiceProperties;
import com.robot.control.messaging.RobotMediaCommandService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
}
