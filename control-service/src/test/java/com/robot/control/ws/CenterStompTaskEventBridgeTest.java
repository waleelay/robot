package com.robot.control.ws;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.control.config.ControlServiceProperties;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.client.RestClient;

class CenterStompTaskEventBridgeTest {

    @Test
    void enablesCenterStompByDefault() {
        assertTrue(new ControlServiceProperties().getCenterStomp().isEnabled());
    }

    @Test
    void forwardsTaskInvalidationOnceAndIgnoresOtherEvents() {
        MediaWebSocketPublisher publisher = mock(MediaWebSocketPublisher.class);
        CenterStompTaskEventBridge bridge = new CenterStompTaskEventBridge(
                new ControlServiceProperties(),
                new ObjectMapper(),
                publisher,
                mock(TaskScheduler.class),
                RestClient.builder());
        byte[] taskEvent = json("""
                {"specversion":"1.0","id":"1001","source":"control","type":"task.changed.v1",
                 "data":{"scopes":["PLAN","EXECUTION"]}}
                """);

        bridge.handleEvent(taskEvent);
        bridge.handleEvent(taskEvent);
        bridge.handleEvent(json("""
                {"specversion":"1.0","id":"1002","source":"control","type":"alarm.changed.v1","data":{}}
                """));

        verify(publisher, times(1)).publish(eq("management.task.invalidated"), org.mockito.ArgumentMatchers.any());
        verify(publisher, never()).publish(eq("panorama.task.changed"), org.mockito.ArgumentMatchers.any());
    }

    private byte[] json(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
