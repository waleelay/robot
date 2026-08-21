package com.robot.bigscreen.ws;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.bigscreen.auth.AuthenticatedRequestHeaders;
import com.robot.bigscreen.config.CenterServiceProperties;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class BigscreenWebSocketAuthorizationServiceTest {

    private final BigscreenWebSocketAuthorizationService service = new BigscreenWebSocketAuthorizationService(
            new CenterServiceProperties(),
            mock(AuthenticatedRequestHeaders.class),
            RestClient.builder(),
            new ObjectMapper());

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
}
