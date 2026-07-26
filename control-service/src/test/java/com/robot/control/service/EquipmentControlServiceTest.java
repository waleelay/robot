package com.robot.control.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.robot.control.auth.CurrentUser;
import com.robot.control.client.ControlManagementClient;
import com.robot.control.messaging.EquipmentControlCommandPublisher;
import com.robot.control.ws.MediaWebSocketPublisher;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EquipmentControlServiceTest {

    private final EquipmentControlCommandPublisher commandPublisher = mock(EquipmentControlCommandPublisher.class);
    private final MediaWebSocketPublisher webSocketPublisher = mock(MediaWebSocketPublisher.class);
    private final ControlManagementClient managementClient = mock(ControlManagementClient.class);
    private final EquipmentControlService service =
            new EquipmentControlService(commandPublisher, webSocketPublisher, managementClient);

    @Test
    void buildsDriveVelocityWithExistingRobotProtocolFields() {
        register(component("BODY", "base"));

        Map<String, Object> payload = publish("base", "drive.velocity", object(
                "linearX", 0.3,
                "linearY", 0.2,
                "angularZ", -0.2));

        assertThat(map(payload.get("params"))).containsExactly(
                entry("linearX", 0.3),
                entry("linearY", 0.0),
                entry("angularZ", -0.2));
        assertTarget(payload, "base", "WHEELED_BASE");
    }

    @Test
    void buildsPtzTimedMoveWithExistingRobotProtocolFields() {
        register(component("PTZ", "ptz-new-001"));

        Map<String, Object> payload = publish("ptz-new-001", "left_up", object(
                "speed", 20.0,
                "duration", 0.3));

        assertThat(map(payload.get("params"))).containsExactly(
                entry("speed", 20.0),
                entry("duration", 0.3));
        assertTarget(payload, "ptz-new-001", "DUAL_LIGHT_PTZ");
    }

    @Test
    void buildsSpeakerCommandsWithExistingRobotProtocolFields() {
        register(component("SPEAKER", "speaker-main"));

        Map<String, Object> volume = publish("speaker-main", "set_volume", object("volume", 55));
        Map<String, Object> mute = publish("speaker-main", "set_mute", object("muted", true));

        assertThat(map(volume.get("params"))).containsExactly(entry("volumePercent", 55));
        assertThat(map(mute.get("params"))).containsExactly(entry("mute", true));
    }

    @Test
    void buildsLauncherCommandsWithExistingRobotProtocolFields() {
        register(component("PAYLOAD", "launcher_38mm"));

        Map<String, Object> safety = publish("launcher_38mm", "set_safety", object("enabled", true));
        Map<String, Object> fire = publish("launcher_38mm", "fire", object("tube", 3));

        assertThat(map(safety.get("params"))).containsExactly(
                entry("safety_on", true),
                entry("wait_status", true));
        assertThat(map(fire.get("params"))).containsExactly(
                entry("tube", 3),
                entry("waitStatusAfterFire", true),
                entry("keepSafetyOn", false));
    }

    @Test
    void buildsWarningLightCommandWithExistingRobotProtocolFields() {
        register(component("PAYLOAD", "warning_light"));

        Map<String, Object> payload = publish("warning-light-left", "set_state", object("enabled", true));

        assertThat(map(payload.get("params"))).containsExactly(
                entry("lightId", "light-001"),
                entry("powerOn", true));
        assertTarget(payload, "warning-light-left", "WARNING_LIGHT");
    }

    @Test
    void buildsVehicleLightCommandWithExistingRobotProtocolFields() {
        register(component(
                "VEHICLE_LIGHT",
                "vehicle-light-main",
                action("LIGHT_VEHICLE_SET")));

        Map<String, Object> payload = publish("vehicle-light-main", "light.vehicle.set", object(
                "front", object("mode", "CUSTOM", "brightness", 70),
                "rear", object("mode", "BREATH", "brightness", 80)));

        assertThat(map(map(payload.get("params")).get("front"))).containsExactly(
                entry("mode", "CUSTOM"),
                entry("brightness", 70));
        assertThat(map(map(payload.get("params")).get("rear"))).containsExactly(
                entry("mode", "BREATH"),
                entry("brightness", 0));
        assertTarget(payload, "vehicle-light-main", "VEHICLE_LIGHT");
    }

    @Test
    void replacesClientDeviceIdButPreservesRuntimeStatus() {
        register(component("PTZ", "ptz-new-001"));
        Map<String, Object> state = service.handleClientState(object(
                "robotId", "robot-001",
                "type", "WHEELED_ROBOT",
                "status", "online",
                "devices", List.of(object(
                        "deviceId", "ptz-dual-001",
                        "bindingId", "old-binding",
                        "deviceType", "DUAL_LIGHT_PTZ",
                        "status", object("pan", 0.15, "moving", true)))));

        List<Map<String, Object>> devices = maps(state.get("devices"));
        assertThat(devices).singleElement().satisfies(device -> {
            assertThat(device)
                    .containsEntry("deviceId", "ptz-new-001")
                    .containsEntry("bindingId", "old-binding")
                    .containsEntry("deviceType", "DUAL_LIGHT_PTZ");
            assertThat(map(device.get("status")))
                    .containsEntry("pan", 0.15)
                    .containsEntry("moving", true);
        });
    }

    private Map<String, Object> publish(String deviceId, String action, Map<String, Object> params) {
        service.publishCommand("robot-001", object(
                "target", object("deviceId", deviceId),
                "action", action,
                "params", params,
                "client", object("seq", 7)), operator());
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(commandPublisher).publishCommand(eq("robot-001"), captor.capture());
        Map<String, Object> payload = map(captor.getValue());
        reset(commandPublisher);
        return payload;
    }

    private void register(Map<String, Object> component) {
        when(managementClient.deviceBySerialNumber("robot-001"))
                .thenReturn(Optional.of(object(
                        "serialNumber", "robot-001",
                        "deviceType", "WHEELED_ROBOT",
                        "components", List.of(component))));
    }

    private Map<String, Object> component(String type, String code, Map<String, Object>... actions) {
        List<Map<String, Object>> actionList = List.of(actions);
        return object(
                "componentType", type,
                "code", code,
                "name", code,
                "capabilities", actionList.isEmpty()
                        ? List.of()
                        : List.of(object("code", type + "_CONTROL", "actions", actionList)));
    }

    private Map<String, Object> action(String code) {
        return object("code", code);
    }

    private CurrentUser operator() {
        return new CurrentUser(
                "operator-1",
                "org-1",
                Set.of("EQUIPMENT_OPERATOR"),
                "terminal-1");
    }

    private void assertTarget(Map<String, Object> payload, String deviceId, String deviceType) {
        assertThat(map(payload.get("target"))).containsExactly(
                entry("deviceId", deviceId),
                entry("deviceType", deviceType));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> maps(Object value) {
        return (List<Map<String, Object>>) value;
    }

    private static Map.Entry<String, Object> entry(String key, Object value) {
        return Map.entry(key, value);
    }

    private static Map<String, Object> object(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index < values.length - 1; index += 2) {
            map.put(String.valueOf(values[index]), values[index + 1]);
        }
        return map;
    }
}
