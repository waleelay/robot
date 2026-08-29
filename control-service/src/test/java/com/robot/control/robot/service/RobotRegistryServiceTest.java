package com.robot.control.robot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.control.config.ControlServiceProperties;
import com.robot.control.ws.MediaWebSocketPublisher;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RobotRegistryServiceTest {

    @Test
    void runtimeSnapshotAndEventsShareVersionAndIgnoreMediaTelemetry() {
        MediaWebSocketPublisher publisher = mock(MediaWebSocketPublisher.class);
        RobotRegistryService service = new RobotRegistryService(new ControlServiceProperties(), publisher, new ObjectMapper());
        service.update(object("robotId", "robot-1", "stateSource", "MEDIA_CLIENT_STATUS", "battery", 99, "speed", 4));
        assertThat(service.find("robot-1").orElseThrow().battery()).isNull();
        assertThat(service.find("robot-1").orElseThrow().controlMode()).isNull();
        service.update(object("robotId", "robot-1", "stateSource", "EDGE_DEVICE_STATUS", "status", "online",
                "battery", 0, "speed", 0.0, "controlMode", "导航模式"));
        var snapshot = service.find("robot-1").orElseThrow();
        assertThat(snapshot.speed()).isEqualTo(0.0);
        assertThat(snapshot.battery()).isZero();
        assertThat(snapshot.runtimeUpdatedAt()).isNotBlank();
        service.update(object("robotId", "robot-1", "stateSource", "MEDIA_CLIENT_STATUS",
                "battery", 99, "speed", 4, "controlMode", "手动模式"));
        var after = service.find("robot-1").orElseThrow();
        assertThat(after.runtimeUpdatedAt()).isEqualTo(snapshot.runtimeUpdatedAt());
        assertThat(after.controlMode()).isEqualTo("导航模式");
        assertThat(after.speed()).isEqualTo(0.0);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> events = ArgumentCaptor.forClass(Map.class);
        verify(publisher, org.mockito.Mockito.times(3)).publish(eq("robot.state"), events.capture());
        assertThat(events.getAllValues().get(2)).containsEntry("runtimeUpdatedAt", snapshot.runtimeUpdatedAt())
                .containsEntry("battery", 0).containsEntry("speed", 0.0);
        service.update(object("robotId", "robot-1", "stateSource", "EDGE_DEVICE_STATUS", "status", "online", "controlMode", null));
        assertThat(service.find("robot-1").orElseThrow().controlMode()).isNull();
    }

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
                "stateSource", "EDGE_DEVICE_STATUS",
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
                "status", "online",
                "stateSource", "EDGE_DEVICE_STATUS"));

        service.sweepOffline();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(publisher, org.mockito.Mockito.times(2)).publish(eq("robot.state"), captor.capture());
        Map<String, Object> state = captor.getAllValues().get(1);
        assertThat(state)
                .containsEntry("robotId", "test116")
                .containsEntry("status", "offline")
                .containsEntry("stateSource", "OFFLINE_SCAN")
                .containsKey("statusChangedAt");
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
                "status", "online",
                "stateSource", "EDGE_DEVICE_STATUS"));

        service.sweepOffline();

        assertThat(service.list()).isEmpty();
        verify(publisher, org.mockito.Mockito.times(2)).publish(eq("robot.state"), any());
    }

    @Test
    void removePublishesOfflineAndDeletesDevice() {
        MediaWebSocketPublisher publisher = mock(MediaWebSocketPublisher.class);
        RobotRegistryService service = new RobotRegistryService(
                new ControlServiceProperties(), publisher, new ObjectMapper());
        service.update(object(
                "robotId", "unknown-robot",
                "status", "online"));

        service.remove("unknown-robot");

        assertThat(service.list()).isEmpty();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(publisher, org.mockito.Mockito.times(2)).publish(eq("robot.state"), captor.capture());
        assertThat(captor.getAllValues().get(1))
                .containsEntry("robotId", "unknown-robot")
                .containsEntry("status", "offline")
                .containsEntry("stateSource", "UNREGISTERED_DEVICE");
    }

    @Test
    void publishesManagementTypeNameAndTypeCode() {
        MediaWebSocketPublisher publisher = mock(MediaWebSocketPublisher.class);
        RobotRegistryService service = new RobotRegistryService(
                new ControlServiceProperties(), publisher, new ObjectMapper());

        service.update(object(
                "robotId", "dog-001",
                "status", "online",
                "typeCode", "ROBOT_DOG",
                "type", "机器狗"));

        assertThat(service.list()).singleElement().satisfies(robot -> {
            assertThat(robot.type()).isEqualTo("机器狗");
            assertThat(robot.typeCode()).isEqualTo("ROBOT_DOG");
        });
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(publisher).publish(eq("robot.state"), captor.capture());
        assertThat(captor.getValue())
                .containsEntry("type", "机器狗")
                .containsEntry("typeCode", "ROBOT_DOG");
    }

    @Test
    void doesNotInventRobotTypeWhenTypeIsMissing() {
        MediaWebSocketPublisher publisher = mock(MediaWebSocketPublisher.class);
        RobotRegistryService service = new RobotRegistryService(
                new ControlServiceProperties(), publisher, new ObjectMapper());

        service.update(object(
                "robotId", "m20Pro_01",
                "status", "online"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(publisher).publish(eq("robot.state"), captor.capture());
        assertThat(captor.getValue())
                .containsEntry("robotId", "m20Pro_01")
                .containsEntry("type", null)
                .containsEntry("typeCode", null);
    }

    @Test
    void acceptsManagementRobotTypeLabel() {
        MediaWebSocketPublisher publisher = mock(MediaWebSocketPublisher.class);
        RobotRegistryService service = new RobotRegistryService(
                new ControlServiceProperties(), publisher, new ObjectMapper());

        service.update(object(
                "robotId", "m20Pro_01",
                "status", "online",
                "type", "机器人",
                "typeCode", "ROBOT"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(publisher).publish(eq("robot.state"), captor.capture());
        assertThat(captor.getValue())
                .containsEntry("type", "机器人")
                .containsEntry("typeCode", "ROBOT");
    }

    @Test
    void keepsExistingTypeWhenRealtimeStatusDoesNotContainType() {
        MediaWebSocketPublisher publisher = mock(MediaWebSocketPublisher.class);
        RobotRegistryService service = new RobotRegistryService(
                new ControlServiceProperties(), publisher, new ObjectMapper());

        service.update(object(
                "robotId", "m20Pro_01",
                "status", "online",
                "type", "机器狗",
                "typeCode", "ROBOT_DOG"));
        service.update(object(
                "robotId", "m20Pro_01",
                "status", "online",
                "stateSource", "EDGE_DEVICE_STATUS",
                "battery", 82));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(publisher, org.mockito.Mockito.times(2)).publish(eq("robot.state"), captor.capture());
        assertThat(captor.getAllValues().get(1))
                .containsEntry("type", "机器狗")
                .containsEntry("typeCode", "ROBOT_DOG")
                .containsEntry("battery", 82);
    }

    @Test
    void onlyEdgeDeviceStatusCanChangeRobotOnlineStatus() {
        MediaWebSocketPublisher publisher = mock(MediaWebSocketPublisher.class);
        RobotRegistryService service = new RobotRegistryService(
                new ControlServiceProperties(), publisher, new ObjectMapper());

        service.update(object(
                "robotId", "study",
                "status", "online",
                "stateSource", "MEDIA_CLIENT_STATUS"));
        assertThat(service.find("study").orElseThrow().status()).isEqualTo("offline");

        service.update(object(
                "robotId", "study",
                "status", "online",
                "stateSource", "EDGE_DEVICE_STATUS"));
        var online = service.find("study").orElseThrow();
        assertThat(online.status()).isEqualTo("online");
        assertThat(online.statusChangedAt()).isNotBlank();

        service.update(object(
                "robotId", "study",
                "status", "offline",
                "stateSource", "MEDIA_CLIENT_STATUS"));
        assertThat(service.find("study").orElseThrow().status()).isEqualTo("online");
    }

    @Test
    void edgeStatusTimeoutIsNotExtendedByMediaClientStatus() {
        MediaWebSocketPublisher publisher = mock(MediaWebSocketPublisher.class);
        ControlServiceProperties properties = new ControlServiceProperties();
        properties.getRobot().setHeartbeatTimeoutSeconds(-1);
        RobotRegistryService service = new RobotRegistryService(
                properties, publisher, new ObjectMapper());

        service.update(object(
                "robotId", "study",
                "status", "online",
                "stateSource", "EDGE_DEVICE_STATUS"));
        service.update(object(
                "robotId", "study",
                "status", "online",
                "stateSource", "MEDIA_CLIENT_STATUS"));
        service.sweepOffline();

        assertThat(service.find("study").orElseThrow().status()).isEqualTo("offline");
    }

    @Test
    void statusVersionRemainsOrderedAcrossSameSecondOfflineAndRecovery() {
        MediaWebSocketPublisher publisher = mock(MediaWebSocketPublisher.class);
        ControlServiceProperties properties = new ControlServiceProperties();
        properties.getRobot().setHeartbeatTimeoutSeconds(-1);
        RobotRegistryService service = new RobotRegistryService(properties, publisher, new ObjectMapper());

        service.update(object(
                "robotId", "study",
                "status", "online",
                "stateSource", "EDGE_DEVICE_STATUS"));
        OffsetDateTime onlineAt = OffsetDateTime.parse(service.find("study").orElseThrow().statusChangedAt());
        service.sweepOffline();
        OffsetDateTime offlineAt = OffsetDateTime.parse(service.find("study").orElseThrow().statusChangedAt());
        service.update(object(
                "robotId", "study",
                "status", "online",
                "stateSource", "EDGE_DEVICE_STATUS"));
        OffsetDateTime recoveredAt = OffsetDateTime.parse(service.find("study").orElseThrow().statusChangedAt());

        assertThat(offlineAt).isAfter(onlineAt);
        assertThat(recoveredAt).isAfter(offlineAt);
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
