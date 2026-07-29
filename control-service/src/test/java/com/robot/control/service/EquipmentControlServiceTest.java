package com.robot.control.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
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
        register(object(
                "componentType", "BODY",
                "code", "body",
                "name", "机器人本体",
                "capabilities", List.of(object(
                        "code", "DEVICE_CONTROL",
                        "actions", List.of(action("SET_LIGHTS"))))));

        List<Map<String, Object>> devices = maps(service.controlProfile("robot-001").get("devices"));
        assertThat(devices)
                .filteredOn(device -> "vehicle-light".equals(device.get("deviceId")))
                .singleElement()
                .satisfies(device -> {
                    assertThat(device)
                            .containsEntry("deviceType", "VEHICLE_LIGHT")
                            .containsEntry("displayName", "车灯光")
                            .containsEntry("actions", List.of("light.vehicle.set"));
                });

        Map<String, Object> payload = publish("vehicle-light", "light.vehicle.set", object(
                "front", object("mode", "CUSTOM", "brightness", 70),
                "rear", object("mode", "BREATH", "brightness", 80)));

        assertThat(map(map(payload.get("params")).get("front"))).containsExactly(
                entry("mode", "CUSTOM"),
                entry("brightness", 70));
        assertThat(map(map(payload.get("params")).get("rear"))).containsExactly(
                entry("mode", "BREATH"),
                entry("brightness", 0));
        assertTarget(payload, "vehicle-light", "VEHICLE_LIGHT");
    }

    @Test
    void buildsMultiFunctionCommandsWithPlatformSemanticFields() {
        register(component(
                "MULTI_FUNCTION_BROADCASTER",
                "broadcaster-001",
                action("SET_VOLUME"),
                action("START_MONITOR"),
                action("SET_MONITOR_SUPPRESSED"),
                action("PLAY_TTS"),
                action("PLAY_AUDIO_FILE"),
                action("LIGHT_SET"),
                action("SET_LIGHT_TILT")));

        Map<String, Object> volume = publish("broadcaster-001", "set_volume", object("volumePercent", 60));
        Map<String, Object> monitor = publish(
                "broadcaster-001", "start_monitor", object("mediaSessionId", "mas-001"));
        Map<String, Object> suppressed = publish(
                "broadcaster-001", "set_monitor_suppressed", object("suppressed", true));
        Map<String, Object> tts = publish("broadcaster-001", "play_tts", object(
                "text", "请注意安全",
                "voice", "male",
                "loop", false));
        Map<String, Object> file = publish("broadcaster-001", "play_audio_file", object(
                "fileName", "notice.mp3",
                "loop", true));
        Map<String, Object> light = publish("broadcaster-001", "light.set", object(
                "brightness", 70,
                "redBlueMode", 2));
        Map<String, Object> tilt = publish(
                "broadcaster-001", "set_light_tilt", object("positionPercent", 80));

        assertThat(map(volume.get("params"))).containsExactly(entry("volumePercent", 60));
        assertThat(map(monitor.get("params"))).containsExactly(entry("mediaSessionId", "mas-001"));
        assertThat(map(suppressed.get("params"))).containsExactly(entry("suppressed", true));
        assertThat(map(tts.get("params"))).containsExactly(
                entry("text", "请注意安全"),
                entry("voice", "MALE"),
                entry("loop", false));
        assertThat(map(file.get("params"))).containsExactly(
                entry("fileName", "notice.mp3"),
                entry("loop", true));
        assertThat(map(light.get("params"))).containsExactly(
                entry("brightness", 70),
                entry("redBlueMode", 2));
        assertThat(map(tilt.get("params"))).containsExactly(entry("positionPercent", 80));
        assertTarget(light, "broadcaster-001", "MULTI_FUNCTION_BROADCASTER");
    }

    @Test
    void rejectsInvalidMultiFunctionFields() {
        register(component("MULTI_FUNCTION_BROADCASTER", "broadcaster-001"));

        assertThatThrownBy(() -> publish("broadcaster-001", "light.set", object()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("至少需要一个控制参数");
        assertThatThrownBy(() -> publish(
                "broadcaster-001", "play_audio_file", object("fileName", "../notice.mp3")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能包含路径");
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

    @Test
    void publishesModeChangeWithoutOptimisticallyPublishingRobotState() {
        register(component("BODY", "base"));
        online("MANUAL", 12);
        Map<String, Object> session = acquireBase();

        Map<String, Object> response = service.setControlMode("robot-001", object(
                "controlMode", "NAVIGATION",
                "controlSessionId", session.get("controlSessionId"),
                "observedStateSeq", 12), operator());

        assertThat(response)
                .containsEntry("status", "PUBLISHED")
                .containsEntry("controlMode", "MANUAL")
                .containsEntry("controlModeName", "手动模式")
                .containsEntry("requestedControlMode", "NAVIGATION")
                .containsEntry("requestedControlModeName", "导航模式")
                .containsEntry("stateSeq", 12L);
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(commandPublisher).publishCommand(eq("robot-001"), captor.capture());
        assertThat(map(captor.getValue()))
                .containsEntry("action", "control.mode.set")
                .containsEntry("params", object("controlMode", "NAVIGATION"));
        verify(webSocketPublisher, never()).publish(eq("robot.state"), any());
    }

    @Test
    void rejectsUnsupportedOrIncompleteModeChanges() {
        register(component("BODY", "base"));
        online("MANUAL", 12);
        Map<String, Object> session = acquireBase();

        assertThatThrownBy(() -> service.setControlMode("robot-001", object(
                "controlMode", "ASSISTED",
                "controlSessionId", session.get("controlSessionId"),
                "observedStateSeq", 12), operator()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的控制模式");
        assertThatThrownBy(() -> service.setControlMode("robot-001", object(
                "controlMode", "NAVIGATION"), operator()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("controlSessionId 必填");
    }

    @Test
    void navigationTakeoverAcquiresBaseAndWaitsForRealManualState() {
        register(component("BODY", "base"));
        online("NAVIGATION", 20);

        Map<String, Object> response = service.takeover("robot-001", object(
                "observedStateSeq", 20), operator());

        assertThat(response)
                .containsEntry("status", "ACTIVE")
                .containsEntry("deviceIds", List.of("base"))
                .containsEntry("controlMode", "NAVIGATION")
                .containsEntry("controlModeName", "导航模式")
                .containsEntry("modeChangeStatus", "PUBLISHED")
                .containsEntry("requestedControlMode", "MANUAL")
                .containsEntry("requestedControlModeName", "手动模式")
                .containsKey("controlSessionId");
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(commandPublisher).publishCommand(eq("robot-001"), captor.capture());
        assertThat(map(captor.getValue()))
                .containsEntry("action", "control.mode.set")
                .containsEntry("params", object("controlMode", "MANUAL"));
        verify(webSocketPublisher, never()).publish(eq("robot.state"), any());
    }

    @Test
    void navigationTakeoverDoesNotStealAnotherTerminalSession() {
        register(component("BODY", "base"));
        online("NAVIGATION", 20);
        acquireBase();
        CurrentUser otherTerminal = new CurrentUser(
                "operator-2",
                "org-1",
                Set.of("EQUIPMENT_OPERATOR"),
                "terminal-2");

        Map<String, Object> response = service.takeover("robot-001", object(
                "observedStateSeq", 20), otherTerminal);

        assertThat(response)
                .containsEntry("code", "CONTROL_LOCKED")
                .containsEntry("message", "target is controlled by another terminal");
        verify(commandPublisher, never()).publishCommand(eq("robot-001"), any());
    }

    private Map<String, Object> publish(String deviceId, String action, Map<String, Object> params) {
        Map<String, Object> request = object(
                "target", object("deviceId", deviceId),
                "action", action,
                "params", params,
                "client", object("seq", 7));
        if ("base".equals(deviceId) && "drive.velocity".equals(action)) {
            online("MANUAL", 1);
            request.put("controlSessionId", acquireBase().get("controlSessionId"));
        }
        service.publishCommand("robot-001", request, operator());
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(commandPublisher).publishCommand(eq("robot-001"), captor.capture());
        Map<String, Object> payload = map(captor.getValue());
        reset(commandPublisher);
        return payload;
    }

    private void online(String controlMode, long stateSeq) {
        service.handleClientState(object(
                "robotId", "robot-001",
                "status", "online",
                "controlMode", controlMode,
                "stateSeq", stateSeq,
                "devices", List.of()));
    }

    private Map<String, Object> acquireBase() {
        return service.acquire("robot-001", object(
                "scope", "ROBOT",
                "deviceIds", List.of("base"),
                "actions", List.of("control.mode.set", "drive.velocity")), operator());
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
