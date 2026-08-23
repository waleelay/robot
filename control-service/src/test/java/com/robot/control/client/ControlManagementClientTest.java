package com.robot.control.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.robot.control.auth.RequestAuthorizationHeaders;
import com.robot.control.config.ControlProperties;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ControlManagementClientTest {

    @Test
    void capsDeviceAuthorizationCacheAtThirtySeconds() {
        ControlProperties properties = new ControlProperties();
        properties.setDeviceCacheTtlSeconds(300);
        ControlManagementClient client = new ControlManagementClient(
                mock(RestClient.class), properties, mock(RequestAuthorizationHeaders.class));

        assertThat(client.deviceTtl()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void keepsWarmedDeviceDetailsIsolatedToCurrentUser() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ControlProperties properties = new ControlProperties();
        properties.setManagementServiceBaseUrl("http://management.test");
        RequestAuthorizationHeaders authorizationHeaders = mock(RequestAuthorizationHeaders.class);
        when(authorizationHeaders.currentCacheKey()).thenReturn(Optional.of("bearer:user-001"));
        ControlManagementClient client = new ControlManagementClient(
                builder.build(), properties, authorizationHeaders);

        server.expect(requestTo("http://management.test/api/v1/management/devices?pageNum=1&pageSize=500"))
                .andRespond(withSuccess("""
                        {"code":"0","data":{"records":[
                          {"id":101,"serialNumber":"robot-001","name":"巡检机器人","deviceType":"ROBOT_DOG"}
                        ]}}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://management.test/api/v1/management/devices/101"))
                .andRespond(withSuccess("""
                        {"code":"0","data":{"device":{"id":101,"serialNumber":"robot-001"},
                          "components":[{"componentType":"PTZ","componentId":"ptz-main"}]}}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://management.test/api/v1/management/selection-options/dictionaries/device_type"))
                .andRespond(withSuccess("""
                        {"code":"0","data":[{"value":"ROBOT_DOG","label":"机器狗"}]}
                        """, MediaType.APPLICATION_JSON));

        client.warmCurrentUserDeviceCache();

        Map<String, Object> profile = client.cachedDeviceBySerialNumber("robot-001").orElseThrow();
        assertThat((List<?>) profile.get("components")).singleElement();
        assertThat(client.deviceTypeName("ROBOT_DOG")).contains("机器狗");

        when(authorizationHeaders.currentCacheKey()).thenReturn(Optional.empty());

        assertThat(client.cachedDeviceBySerialNumber("robot-001")).isEmpty();
        assertThat(client.deviceTypeName("ROBOT_DOG")).isEmpty();
        server.verify();
    }
}
