package com.robot.control.robot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.control.config.ControlServiceProperties;
import com.robot.control.ws.MediaWebSocketPublisher;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RobotRegistryServiceTest {

    @Test
    void publishesEdgeDynamicFieldsWithoutClearingMountedDevices() {
        MediaWebSocketPublisher publisher = mock(MediaWebSocketPublisher.class);
        RobotRegistryService service = new RobotRegistryService(
                new ControlServiceProperties(), publisher, new ObjectMapper());
        service.update(object(
                "robotId", "test115",
                "status", "online",
                "devices", List.of(object("deviceId", "ptz-001"))));

        service.update(object(
                "robotId", "test115",
                "status", "fault",
                "battery", 47,
                "speed", 0.6,
                "healthStatus", "异常",
                "controlMode", "导航模式",
                "location", object("x", 5.28, "y", 1.37, "z", 0, "yaw", -2.87),
                "edgeStatus", object("basic", object("healthStatus", "异常"))));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(publisher, org.mockito.Mockito.times(2)).publish(eq("robot.state"), captor.capture());
        Map<String, Object> state = captor.getAllValues().get(1);
        assertThat(state)
                .containsEntry("robotId", "test115")
                .containsEntry("status", "fault")
                .containsEntry("battery", 47)
                .containsEntry("speed", 0.6)
                .containsEntry("healthStatus", "异常")
                .containsEntry("controlMode", "导航模式")
                .containsEntry("controlModeName", "导航模式")
                .containsEntry("devices", List.of(object("deviceId", "ptz-001")));
        assertThat(map(state.get("location"))).containsEntry("yaw", -2.87);
    }

    @Test
    void sweepOfflinePublishesOfflineWithOfflineScanSource() {
        MediaWebSocketPublisher publisher = mock(MediaWebSocketPublisher.class);
        ControlServiceProperties properties = new ControlServiceProperties();
        properties.getRobot().setHeartbeatTimeoutSeconds(-1);
        RobotRegistryService service = new RobotRegistryService(
                properties, publisher, new ObjectMapper());
        service.update(object(
                "robotId", "test116",
                "status", "online"));

        service.sweepOffline();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(publisher, org.mockito.Mockito.times(2)).publish(eq("robot.state"), captor.capture());
        Map<String, Object> state = captor.getAllValues().get(1);
        assertThat(state)
                .containsEntry("robotId", "test116")
                .containsEntry("status", "offline")
                .containsEntry("stateSource", "OFFLINE_SCAN");
    }

    @Test
    void sweepOfflineRemovesStaleOfflineEntries() {
        MediaWebSocketPublisher publisher = mock(MediaWebSocketPublisher.class);
        ControlServiceProperties properties = new ControlServiceProperties();
        properties.getRobot().setHeartbeatTimeoutSeconds(-1);
        properties.getRobot().setOfflineRetentionSeconds(-1);
        RobotRegistryService service = new RobotRegistryService(
                properties, publisher, new ObjectMapper());
        service.update(object(
                "robotId", "test120",
                "status", "online"));

        service.sweepOffline();

        assertThat(service.list()).isEmpty();
        verify(publisher, org.mockito.Mockito.times(2)).publish(eq("robot.state"), any());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    private Map<String, Object> object(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length - 1; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }
}
