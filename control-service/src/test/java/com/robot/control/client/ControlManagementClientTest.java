package com.robot.control.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.robot.control.auth.RequestAuthorizationHeaders;
import com.robot.control.config.ControlProperties;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
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

        server.expect(requestTo("http://management.test/api/v1/management/devices?pageNum=1&pageSize=100"))
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

    @Test
    void readsDevicesAfterTheFirstFiveHundredRecords() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ControlProperties properties = new ControlProperties();
        properties.setManagementServiceBaseUrl("http://management.test");
        RequestAuthorizationHeaders headers = mock(RequestAuthorizationHeaders.class);
        when(headers.currentCacheKey()).thenReturn(Optional.of("user-001"));
        ControlManagementClient client = new ControlManagementClient(builder.build(), properties, headers);

        for (int page = 1; page <= 6; page++) {
            int first = (page - 1) * 100 + 1;
            int count = page < 6 ? 100 : 1;
            server.expect(requestTo("http://management.test/api/v1/management/devices?pageNum=" + page + "&pageSize=100"))
                    .andRespond(withSuccess(recordsJson(first, count, 501), MediaType.APPLICATION_JSON));
        }

        List<Map<String, Object>> devices = client.devices();

        assertThat(devices).hasSize(501);
        assertThat(devices.get(500).get("serialNumber")).isEqualTo("robot-501");
        server.verify();
    }

    @Test
    void stopsWhenManagementRepeatsAFullPage() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ControlProperties properties = new ControlProperties();
        properties.setManagementServiceBaseUrl("http://management.test");
        RequestAuthorizationHeaders headers = mock(RequestAuthorizationHeaders.class);
        when(headers.currentCacheKey()).thenReturn(Optional.of("user-001"));
        ControlManagementClient client = new ControlManagementClient(builder.build(), properties, headers);
        String page = recordsJson(1, 100, 1000);
        server.expect(requestTo("http://management.test/api/v1/management/devices?pageNum=1&pageSize=100"))
                .andRespond(withSuccess(page, MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://management.test/api/v1/management/devices?pageNum=2&pageSize=100"))
                .andRespond(withSuccess(page, MediaType.APPLICATION_JSON));

        assertThatThrownBy(client::devices)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("分页无进展");
        server.verify();
    }

    @Test
    void convertsGisThroughInternalManagementEndpointWithoutUserContext() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ControlProperties properties = new ControlProperties();
        properties.setManagementServiceBaseUrl("http://management.test");
        ControlManagementClient client = new ControlManagementClient(
                builder.build(), properties, mock(RequestAuthorizationHeaders.class));
        server.expect(requestTo("http://management.test/internal/v1/management/maps/gis/convert"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"code":"0","data":{"items":[{"serialNumber":"robot-1","mapId":"map-1",
                          "x":1.2,"y":3.4,"longitude":104.1,"latitude":30.2,"converted":true}]}}
                        """, MediaType.APPLICATION_JSON));

        var results = client.convertGis(List.of(
                new ControlManagementClient.GisCoordinate("robot-1", "map-1", 1.2, 3.4)));

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.serialNumber()).isEqualTo("robot-1");
            assertThat(result.converted()).isTrue();
            assertThat(result.longitude()).isEqualTo(104.1);
            assertThat(result.latitude()).isEqualTo(30.2);
        });
        server.verify();
    }

    private static String recordsJson(int first, int count, int total) {
        String records = IntStream.range(first, first + count)
                .mapToObj(index -> "{\"id\":" + index + ",\"serialNumber\":\"robot-" + index + "\"}")
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        return "{\"code\":\"0\",\"data\":{\"records\":[" + records + "],\"total\":" + total + "}}";
    }
}
