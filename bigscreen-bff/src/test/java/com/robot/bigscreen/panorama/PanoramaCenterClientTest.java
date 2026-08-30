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
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
    private static final String DEVICES_URL =
            "http://management.test/api/v1/management/devices?pageNum=1&pageSize=100";
    private static final String FIXED_CAMERAS_URL =
            "http://management.test/api/v1/management/fixed-cameras?pageNum=1&pageSize=100";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final PanoramaCenterClient client;

    PanoramaCenterClientTest() {
        CenterServiceProperties properties = new CenterServiceProperties();
        properties.setManageBaseUrl("http://management.test");
        client = new PanoramaCenterClient(RestClient.builder(), properties,
                mock(AuthenticatedRequestHeaders.class), 1000, 1500, 16, 1000, 1500, 8);
        ReflectionTestUtils.setField(client, "restClient", builder.build());
        ReflectionTestUtils.setField(client, "taskRestClient", builder.build());
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
    void resourceListForbiddenIsAnAuthorizedEmptySet() {
        server.expect(requestTo(DEVICES_URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));
        server.expect(requestTo(FIXED_CAMERAS_URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertEquals(List.of(), client.devices());
        assertEquals(List.of(), client.fixedCameras());
        assertEquals(16, client.generalRequestPoolSnapshot().get("availablePermits"));
        server.verify();
    }

    @Test
    void resourceListUnauthorizedIsStillAnAuthenticationFailure() {
        server.expect(requestTo(DEVICES_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(requestTo(FIXED_CAMERAS_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertEquals(HttpStatus.UNAUTHORIZED,
                assertThrows(ResponseStatusException.class, client::devices).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED,
                assertThrows(ResponseStatusException.class, client::fixedCameras).getStatusCode());
        server.verify();
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

    @Test
    void opensCircuitAfterThreeManagementServerFailures() {
        for (int index = 0; index < 3; index++) {
            server.expect(requestTo(MAPS_URL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        }
        for (int index = 0; index < 3; index++) {
            assertThrows(ResponseStatusException.class, client::enabledMaps);
        }

        ResponseStatusException error = assertThrows(ResponseStatusException.class, client::enabledMaps);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
        server.verify();
    }

    @Test
    void taskPlansReadAllPagesAndStopAtReportedTotal() {
        String records = IntStream.range(0, 100)
                .mapToObj(index -> "{\"id\":" + index + "}")
                .collect(Collectors.joining(","));
        server.expect(requestTo(
                        "http://management.test/api/v1/management/task-workflow-plans?pageNum=1&pageSize=100&enabled=true"))
                .andRespond(withSuccess(
                        "{\"data\":{\"records\":[" + records + "],\"total\":101}}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(
                        "http://management.test/api/v1/management/task-workflow-plans?pageNum=2&pageSize=100&enabled=true"))
                .andRespond(withSuccess(
                        "{\"data\":{\"records\":[{\"id\":100}],\"total\":101}}",
                        MediaType.APPLICATION_JSON));

        assertEquals(101, client.taskWorkflowPlans().size());
        server.verify();
    }

    @Test
    void taskPaginationStopsWhenManagementRepeatsTheSameFullPage() {
        String records = IntStream.range(0, 100)
                .mapToObj(index -> "{\"id\":" + index + "}")
                .collect(Collectors.joining(","));
        String response = "{\"data\":{\"records\":[" + records + "]}}";
        server.expect(requestTo(
                        "http://management.test/api/v1/management/task-workflow-plans?pageNum=1&pageSize=100&enabled=true"))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        server.expect(requestTo(
                        "http://management.test/api/v1/management/task-workflow-plans?pageNum=2&pageSize=100&enabled=true"))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        PanoramaCenterClient.TaskSourceException error = assertThrows(
                PanoramaCenterClient.TaskSourceException.class,
                client::taskWorkflowPlans);
        assertEquals("TASK_PAGINATION_NO_PROGRESS", error.reasonCode());
        server.verify();
    }
}
