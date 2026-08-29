package com.robot.bigscreen.panorama;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.robot.bigscreen.auth.AuthenticatedRequestHeaders;
import com.robot.bigscreen.config.CenterServiceProperties;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.concurrent.Semaphore;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

class PanoramaCenterClientTest {
    private static final String MAPS_URL =
            "http://management.test/api/v1/management/maps?pageNum=1&pageSize=500&enabled=true";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final PanoramaCenterClient client;

    PanoramaCenterClientTest() {
        CenterServiceProperties properties = new CenterServiceProperties();
        properties.setManageBaseUrl("http://management.test");
        client = new PanoramaCenterClient(RestClient.builder(), properties,
                mock(AuthenticatedRequestHeaders.class), 1000, 1500, 16, 1000, 1500, 8);
        ReflectionTestUtils.setField(client, "restClient", builder.build());
    }

    @Test
    void mapHttpFailuresAreNotEmptyListsAndAuthorizationStatusIsPreserved() {
        for (HttpStatus status : List.of(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN, HttpStatus.BAD_GATEWAY)) {
            server.expect(requestTo(MAPS_URL)).andRespond(withStatus(status));
            ResponseStatusException error = assertThrows(ResponseStatusException.class, client::enabledMaps);
            assertEquals(status == HttpStatus.BAD_GATEWAY ? HttpStatus.SERVICE_UNAVAILABLE : status, error.getStatusCode());
            assertEquals(16, client.generalRequestPoolSnapshot().get("availablePermits"));
            server.verify();
            server.reset();
        }
    }

    @Test
    void mapTimeoutAndEmptyResponseFailButSuccessfulEmptyListIsValid() {
        server.expect(requestTo(MAPS_URL)).andRespond(withException(new SocketTimeoutException("测试地图读取超时")));
        server.expect(requestTo(MAPS_URL)).andRespond(withSuccess());
        server.expect(requestTo(MAPS_URL)).andRespond(withSuccess(
                "{\"code\":\"0\",\"data\":{\"records\":[]}}", MediaType.APPLICATION_JSON));
        assertThrows(ResponseStatusException.class, client::enabledMaps);
        assertThrows(ResponseStatusException.class, client::enabledMaps);
        assertEquals(List.of(), client.enabledMaps());
        assertEquals(16, client.generalRequestPoolSnapshot().get("availablePermits"));
        server.verify();
    }

    @Test
    void mapConcurrencyLimitIsExplicitFailureAndReleasesNoUnownedPermit() {
        Semaphore permits = new Semaphore(0);
        ReflectionTestUtils.setField(client, "generalRequestPermits", permits);
        ResponseStatusException error = assertThrows(ResponseStatusException.class, client::enabledMaps);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
        assertEquals(0, permits.availablePermits());
    }
}
