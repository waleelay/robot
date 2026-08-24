package com.robot.bigscreen.fixedcamera;

import static org.assertj.core.api.Assertions.assertThat;

import com.robot.bigscreen.config.CenterServiceProperties;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.client.RestClient;

class FixedCameraCatalogLeaseClientTest {

    @Test
    void createsShortSanitizedLeaseWithoutRawIdentity() {
        FixedCameraCatalogLeaseClient client = new FixedCameraCatalogLeaseClient(
                new CenterServiceProperties(), RestClient.builder());
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("sensitive-token");
        Instant now = Instant.parse("2026-08-24T00:00:00Z");

        FixedCameraCatalogLeaseClient.LeaseRequest lease = client.leaseRequest(
                new UsernamePasswordAuthenticationToken("user-001", ""),
                headers,
                List.of(Map.of(
                        "id", "camera-001",
                        "enabled", true,
                        "protocolType", "RTSP",
                        "mainStreamUrl", "rtsp://camera/main")),
                now);

        assertThat(lease.leaseId()).hasSize(64).doesNotContain("user-001", "sensitive-token");
        assertThat(lease.expiresAt()).isEqualTo(now.plusSeconds(180));
        assertThat(lease.cameras()).singleElement().satisfies(camera -> {
            assertThat(camera.cameraId()).isEqualTo("camera-001");
            assertThat(camera.mainStreamUrl()).isEqualTo("rtsp://camera/main");
        });
    }
}
