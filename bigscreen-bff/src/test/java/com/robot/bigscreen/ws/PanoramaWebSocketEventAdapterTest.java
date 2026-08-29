package com.robot.bigscreen.ws;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.bigscreen.panorama.StatsPart;
import java.util.List;
import org.junit.jupiter.api.Test;

class PanoramaWebSocketEventAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PanoramaWebSocketEventAdapter adapter = new PanoramaWebSocketEventAdapter(objectMapper);

    @Test
    void preservesSlamMetadataInPanoramaLocationEvent() throws Exception {
        List<String> events = adapter.adapt("""
                {
                  "event":"robot.state",
                  "timestamp":"2026-08-05 17:07:43",
                  "data":{
                    "robotId":"test115",
                    "status":"online",
                    "healthStatus":"异常",
                    "controlMode":"导航模式",
                    "runtimeUpdatedAt":"2026-08-05T09:07:43.123456789Z",
                    "speed":0,
                    "location":{
                      "localized":true,
                      "coordinateType":"地图坐标",
                      "mapId":"2077",
                      "x":5.28,
                      "y":1.37,
                      "z":0,
                      "yaw":-2.87,
                      "updatedAt":"2026-08-05 17:07:43"
                    }
                  }
                }
                """);

        JsonNode locationEvent = events.stream()
                .map(this::readTree)
                .filter(node -> "panorama.device.location.changed".equals(node.path("event").asText()))
                .findFirst()
                .orElseThrow();
        JsonNode location = locationEvent.path("data").path("location");
        assertThat(location.path("x").asDouble()).isEqualTo(5.28);
        assertThat(location.path("yaw").asDouble()).isEqualTo(-2.87);
        assertThat(location.path("mapId").asText()).isEqualTo("2077");
        assertThat(location.path("coordinateType").asText()).isEqualTo("地图坐标");
        assertThat(location.path("localized").asBoolean()).isTrue();
        JsonNode statusEvent = events.stream()
                .map(this::readTree)
                .filter(node -> "panorama.device.status.changed".equals(node.path("event").asText()))
                .findFirst()
                .orElseThrow();
        assertThat(statusEvent.path("data").path("status").asText()).isEqualTo("fault");
        assertThat(statusEvent.path("data").path("healthStatus").asText()).isEqualTo("异常");
        assertThat(statusEvent.path("data").path("controlMode").asText()).isEqualTo("导航模式");
        assertThat(statusEvent.path("data").path("controlModeName").asText()).isEqualTo("导航模式");
        assertThat(statusEvent.path("data").path("runtimeUpdatedAt").asText()).isEqualTo("2026-08-05T09:07:43.123456789Z");
        assertThat(statusEvent.path("data").path("speed").asDouble()).isZero();
        assertThat(events.stream()
                .map(this::readTree)
                .noneMatch(node -> "panorama.stats.changed".equals(node.path("event").asText()))).isTrue();
        assertThat(adapter.statsRefreshParts("browser-a", """
                {"event":"robot.state","data":{"robotId":"test115","status":"online","healthStatus":"异常"}}
                """)).contains(StatsPart.DEVICES, StatsPart.TASKS);
        assertThat(adapter.statsRefreshParts("browser-a", """
                {"event":"robot.state","data":{"robotId":"test115","status":"online","healthStatus":"异常"}}
                """)).isEmpty();
        assertThat(adapter.statsRefreshParts("browser-b", """
                {"event":"robot.state","data":{"robotId":"test115","status":"online","healthStatus":"异常"}}
                """)).contains(StatsPart.DEVICES, StatsPart.TASKS);
    }

    @Test
    void doesNotInventTelemetryWhenRobotHasNotReportedIt() {
        JsonNode data = adapter.adapt("""
                {"event":"robot.state","data":{"robotId":"robot-1","status":"online"}}
                """).stream().map(this::readTree)
                .filter(event -> "panorama.device.status.changed".equals(event.path("event").asText()))
                .findFirst().orElseThrow().path("data");
        assertThat(data.path("battery").isNull()).isTrue();
        assertThat(data.path("speed").isNull()).isTrue();
        assertThat(data.path("controlMode").isNull()).isTrue();
        assertThat(data.path("runtimeUpdatedAt").isNull()).isTrue();
    }

    @Test
    void suppressesTaskEventWithoutPlanIdAndRequestsAuthoritativeRefresh() {
        String payload = """
                {
                  "event":"panorama.task.changed",
                  "timestamp":"2026-08-13 19:48:14",
                  "data":{
                    "taskId":null,
                    "workflowInstanceId":2087868758369030100,
                    "commandId":"task-3ab936c1-b3a0-459a-95d6-511b6f8142b1",
                    "robotId":"x30_test_26081301",
                    "source":"EDGE_TASK_MQTT",
                    "status":"running"
                  }
                }
                """;

        assertThat(adapter.isTaskInvalidation(payload)).isTrue();
        assertThat(adapter.adapt(payload)).isEmpty();
    }

    @Test
    void forwardsTaskEventWithPlanIdWithoutExtraRefresh() {
        String payload = """
                {
                  "event":"panorama.task.changed",
                  "data":{
                    "taskId":123,
                    "workflowInstanceId":456,
                    "task":{"taskId":123,"status":"running"}
                  }
                }
                """;

        assertThat(adapter.isTaskInvalidation(payload)).isFalse();
        assertThat(adapter.adapt(payload)).containsExactly(payload);
    }

    @Test
    void requestsStatisticsRefreshForMileageChanges() {
        String payload = """
                {
                  "event":"robot.mileage.changed",
                  "data":{"robotId":"test115","deltaMeters":10.2}
                }
                """;

        assertThat(adapter.statsRefreshParts("browser-a", payload)).containsExactly(StatsPart.TASKS);
        assertThat(adapter.adapt(payload)).containsExactly(payload);
    }

    @Test
    void recognizesManagementAlarmInvalidation() {
        String payload = """
                {
                  "event":"management.alarm.invalidated",
                  "data":{"source":"management","eventId":"1001"}
                }
                """;

        assertThat(adapter.isAlarmInvalidation(payload)).isTrue();
        assertThat(adapter.statsRefreshParts("browser-a", payload)).containsExactly(StatsPart.ALARMS);
    }

    @Test
    void skipsDeviceStatusEventForClientStatusSource() throws Exception {
        List<String> events = adapter.adapt("""
                {
                  "event":"robot.state",
                  "timestamp":"2026-08-18 10:00:00",
                  "data":{
                    "robotId":"test117",
                    "status":"online",
                    "stateSource":"MEDIA_CLIENT_STATUS",
                    "battery":80,
                    "controlMode":"手动模式"
                  }
                }
                """);

        assertThat(events.stream()
                .map(this::readTree)
                .noneMatch(node -> "panorama.device.status.changed".equals(node.path("event").asText()))).isTrue();
        assertThat(adapter.statsRefreshParts("browser-a", """
                {"event":"robot.state","data":{"robotId":"test117","status":"online","stateSource":"MEDIA_CLIENT_STATUS"}}
                """)).isEmpty();
    }

    @Test
    void derivesDeviceStatusEventForEdgeStatusSource() throws Exception {
        List<String> events = adapter.adapt("""
                {
                  "event":"robot.state",
                  "timestamp":"2026-08-18 10:00:00",
                  "data":{
                    "robotId":"test118",
                    "status":"online",
                    "healthStatus":"异常",
                    "stateSource":"EDGE_DEVICE_STATUS"
                  }
                }
                """);

        JsonNode statusEvent = events.stream()
                .map(this::readTree)
                .filter(node -> "panorama.device.status.changed".equals(node.path("event").asText()))
                .findFirst()
                .orElseThrow();
        assertThat(statusEvent.path("data").path("status").asText()).isEqualTo("fault");
    }

    @Test
    void derivesOfflineDeviceStatusEventForOfflineScanSource() throws Exception {
        List<String> events = adapter.adapt("""
                {
                  "event":"robot.state",
                  "timestamp":"2026-08-18 10:00:00",
                  "data":{
                    "robotId":"test119",
                    "status":"offline",
                    "stateSource":"OFFLINE_SCAN"
                  }
                }
                """);

        JsonNode statusEvent = events.stream()
                .map(this::readTree)
                .filter(node -> "panorama.device.status.changed".equals(node.path("event").asText()))
                .findFirst()
                .orElseThrow();
        assertThat(statusEvent.path("data").path("status").asText()).isEqualTo("offline");
        assertThat(statusEvent.path("data").path("statusChangedAt").asText())
                .isEqualTo("2026-08-18 10:00:00");
        assertThat(adapter.statsRefreshParts("browser-a", """
                {"event":"robot.state","data":{"robotId":"test119","status":"offline","stateSource":"OFFLINE_SCAN"}}
                """)).contains(StatsPart.DEVICES, StatsPart.TASKS);
    }

    @Test
    void convertsFixedCameraHealthToUserScopedInvalidationAndRefreshesStats() {
        String payload = """
                {"event":"fixed-camera.health.changed","timestamp":"2026-08-23 23:00:00",
                 "data":{"scope":"CAMERA","cameraId":"camera-001","status":"AVAILABLE"}}
                """;

        assertThat(adapter.isFixedCameraHealthInvalidation(payload)).isTrue();
        assertThat(readTree(adapter.fixedCameraHealthInvalidation(payload)).path("event").asText())
                .isEqualTo("bigscreen.fixed-camera.health.changed");
        assertThat(adapter.statsRefreshParts("browser-a", payload)).containsExactly(StatsPart.DEVICES);
        assertThat(adapter.adapt(payload)).isEmpty();
    }

    private JsonNode readTree(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (Exception exception) {
            throw new IllegalArgumentException(exception);
        }
    }
}
