package com.robot.bigscreen.ws;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.bigscreen.auth.AuthenticatedRequestHeaders;
import com.robot.bigscreen.config.CenterServiceProperties;
import com.robot.bigscreen.fixedcamera.FixedCameraCatalogLeaseClient;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

class BigscreenWebSocketAuthorizationServiceTest {

    private final BigscreenWebSocketAuthorizationService service = new BigscreenWebSocketAuthorizationService(
            new CenterServiceProperties(),
            mock(AuthenticatedRequestHeaders.class),
            RestClient.builder(),
            new ObjectMapper(),
            mock(FixedCameraCatalogLeaseClient.class));

    @Test
    void treatsForbiddenResourceQueriesAsRevokedAuthorization() {
        Set<String> resources = service.emptyWhenForbidden("设备", () -> {
            throw HttpClientErrorException.create(
                    HttpStatus.FORBIDDEN, "Forbidden", HttpHeaders.EMPTY, null, null);
        }, Set.of());

        assertTrue(resources.isEmpty());
    }

    @Test
    void keepsManagementFailuresDistinctFromAuthorizationRevocation() {
        assertThrows(RestClientResponseException.class,
                () -> service.emptyWhenForbidden("设备", () -> {
                    throw HttpServerErrorException.create(
                            HttpStatus.SERVICE_UNAVAILABLE, "Unavailable", HttpHeaders.EMPTY, null, null);
                }, Set.of()));
    }

    @Test
    void rejectsUpstreamEventsWithoutResourceIdentity() {
        assertFalse(service.canReceive(Set.of(), "{\"event\":\"panorama.stats.changed\",\"data\":{\"total\":1}}"));
    }

    @Test
    void filtersRobotStateOutsideAuthorizedDevices() {
        String payload = """
                {
                  "event": "robot.state",
                  "data": {
                    "robotId": "x30_test_26081302",
                    "status": "online"
                  }
                }
                """;

        assertFalse(service.canReceive(Set.of("m20Pro_01"), payload));
        assertTrue(service.canReceive(Set.of("m20Pro_01", "x30_test_26081302"), payload));
    }

    @Test
    void authorizesRobotStateByRobotIdWithoutTreatingMountedCamerasAsFixedCameras() {
        String payload = """
                {
                  "event": "robot.state",
                  "data": {
                    "robotId": "sx-songling-001",
                    "status": "online",
                    "cameras": [
                      {"cameraId": "camera01", "deviceId": "camera01"},
                      {"cameraId": "camera02", "deviceId": "camera02"}
                    ],
                    "devices": [
                      {
                        "deviceId": "gimbal-001",
                        "status": {
                          "cameraId": "camera01",
                          "cameraIds": ["camera01", "camera02"]
                        }
                      }
                    ]
                  }
                }
                """;

        assertTrue(service.canReceive(
                new BigscreenWebSocketAuthorizationService.AuthorizedResources(
                        Set.of("sx-songling-001"), Set.of()),
                payload));
        assertFalse(service.canReceive(
                new BigscreenWebSocketAuthorizationService.AuthorizedResources(
                        Set.of("another-robot"), Set.of("camera01", "camera02")),
                payload));
    }

    @Test
    void extractsNestedAlarmRobotId() {
        String payload = """
                {
                  "event": "panorama.alarm.changed",
                  "data": {
                    "alarm": {
                      "alarmId": "alarm-001",
                      "robotId": "m20Pro_01"
                    }
                  }
                }
                """;

        assertTrue(service.canReceive(Set.of("m20Pro_01"), payload));
        assertFalse(service.canReceive(Set.of("x30_test_26081302"), payload));
    }

    @Test
    void authorizesFixedCameraEventBySourceId() {
        String payload = """
                {
                  "event": "video.session.changed",
                  "data": {
                    "sourceType": "FIXED_CAMERA",
                    "sourceId": "camera-001"
                  }
                }
                """;

        assertTrue(service.canReceive(
                new BigscreenWebSocketAuthorizationService.AuthorizedResources(
                        Set.of(), Set.of("camera-001")),
                payload));
        assertFalse(service.canReceive(
                new BigscreenWebSocketAuthorizationService.AuthorizedResources(
                        Set.of(), Set.of("camera-002")),
                payload));
    }

    @Test
    void rejectsClientCommandForUnauthorizedRobot() {
        String payload = """
                {
                  "type": "control.command",
                  "requestId": "request-001",
                  "payload": {
                    "robotId": "robot-002",
                    "action": "move"
                  }
                }
                """;

        assertFalse(service.canForwardClientMessage(
                new BigscreenWebSocketAuthorizationService.AuthorizedResources(
                        Set.of("robot-001"), Set.of()),
                payload));
        assertTrue(service.canForwardClientMessage(
                new BigscreenWebSocketAuthorizationService.AuthorizedResources(
                        Set.of("robot-001", "robot-002"), Set.of()),
                payload));
    }

    @Test
    void keepsClientCameraIdPreAuthorization() {
        String payload = """
                {
                  "type": "video.session.start",
                  "requestId": "request-camera-001",
                  "payload": {
                    "cameraId": "camera-001"
                  }
                }
                """;

        assertTrue(service.canForwardClientMessage(
                new BigscreenWebSocketAuthorizationService.AuthorizedResources(
                        Set.of(), Set.of("camera-001")),
                payload));
        assertFalse(service.canForwardClientMessage(
                new BigscreenWebSocketAuthorizationService.AuthorizedResources(
                        Set.of(), Set.of("camera-002")),
                payload));
    }

    @Test
    void allowsClientQueryWithoutExplicitResourceForControlFinalAuthorization() {
        String payload = """
                {
                  "type": "video.intercom.call.query",
                  "requestId": "request-002",
                  "payload": {}
                }
                """;

        assertTrue(service.canForwardClientMessage(
                new BigscreenWebSocketAuthorizationService.AuthorizedResources(Set.of(), Set.of()),
                payload));
    }

}
