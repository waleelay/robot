package com.robot.bigscreen.ws;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class PanoramaTaskInvalidationKeyTest {

    private final PanoramaWebSocketEventAdapter adapter = new PanoramaWebSocketEventAdapter(new ObjectMapper());

    @Test
    void extractsStableCorrelationKeyFromManagementTaskInvalidation() {
        String payload = """
                {"event":"management.task.invalidated","data":{"source":"management","eventId":"1001"}}
                """;

        assertThat(adapter.taskInvalidationKey(payload)).isEqualTo("management:1001");
    }

    @Test
    void ignoresOtherEventsAndIncompleteIdentifiers() {
        assertThat(adapter.taskInvalidationKey("{\"event\":\"panorama.task.changed\",\"data\":{}}"))
                .isNull();
        assertThat(adapter.taskInvalidationKey("{\"event\":\"management.task.invalidated\",\"data\":{}}"))
                .isNull();
    }
}
