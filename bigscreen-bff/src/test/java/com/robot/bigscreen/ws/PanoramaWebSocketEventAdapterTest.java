package com.robot.bigscreen.ws;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        JsonNode statsEvent = events.stream()
                .map(this::readTree)
                .filter(node -> "panorama.stats.changed".equals(node.path("event").asText()))
                .findFirst()
                .orElseThrow();
        assertThat(statsEvent.path("data").path("deviceStats").path("fault").asInt()).isEqualTo(1);
    }

    private JsonNode readTree(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (Exception exception) {
            throw new IllegalArgumentException(exception);
        }
    }
}
