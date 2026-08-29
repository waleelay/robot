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

    @Test
    void unknownReportedModeCannotAuthorizeMovement() {
        register(component("BODY", "body"));
        online(null);
        assertThat(service.controlProfile("robot-001").get("controlMode")).isNull();
        assertThatThrownBy(() -> service.publishCommand("robot-001", object("target", object("deviceId", "base"),
                "action", "drive.velocity", "params", object("linearX", 0.2)), operator()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("模式未知");
        verify(commandPublisher, never()).publishCommand(eq("robot-001"), any());
    }

    private final EquipmentControlCommandPublisher commandPublisher = mock(EquipmentControlCommandPublisher.class);
    private final MediaWebSocketPublisher webSocketPublisher = mock(MediaWebSocketPublisher.class);
    private final ControlManagementClient managementClient = mock(ControlManagementClient.class);
    private final EquipmentControlService service =
            new EquipmentControlService(commandPublisher, webSocketPublisher, managementClient);

    @Test
    void rejectsCommandBeforeMqttWhenCurrentIdentityCannotAccessRobot() {
        when(managementClient.deviceBySerialNumber("robot-001")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.publishCommand(
                "robot-001",
                object(
                        "target", object("deviceId", "base"),
                        "action", "drive.velocity",
                        "params", object("linearX", 0.2, "linearY", 0.0, "angularZ", 0.0)),
                operator()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未找到机器人");

        verify(commandPublisher, never()).publishCommand(eq("robot-001"), any());
    }

    @Test
    void buildsDriveVelocityWithExistingRobotProtocolFields() {
        register(component("BODY", "body"));

        assertThat(maps(service.controlProfile("robot-001").get("devices")))
                .filteredOn(device -> "WHEELED_BASE".equals(device.get("deviceType")))
                .singleElement()
                .satisfies(device -> assertThat(device)
                        .containsEntry("deviceId", "base")
                        .containsEntry("scope", "BODY")
                        .containsEntry("actions", List.of(
                                "drive.velocity",
                                "navigation.return_home",
                                "docking.leave")));

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
    void mapsAndBuildsRegisteredPtzZoomAndAutoRotateCommands() {
        register(component(
                "PTZ",
                "ptz-main",
                action("PTZ_ZOOM_IN"),
                action("PTZ_ZOOM_OUT"),
                action("PTZ_AUTO_ROTATE")));

        assertThat(maps(service.controlProfile("robot-001").get("devices")))
                .filteredOn(device -> "ptz-main".equals(device.get("deviceId")))
                .singleElement()
                .satisfies(device -> assertThat(device)
                        .containsEntry("deviceType", "DUAL_LIGHT_PTZ")
                        .containsEntry("actions", List.of("zoom_in", "zoom_out", "auto_rotate")));

        Map<String, Object> zoomIn = publish("ptz-main", "zoom_in", object(
                "speed", 20.0,
                "duration", 0.3));
        Map<String, Object> zoomOut = publish("ptz-main", "zoom_out", object(
                "speed", 30.0,
                "duration", 0.4));
        Map<String, Object> autoRotate = publish("ptz-main", "auto_rotate", object(
                "enabled", true,
                "speed", 20.0));

        assertThat(map(zoomIn.get("params"))).containsExactly(
                entry("speed", 20.0),
                entry("duration", 0.3));
        assertThat(map(zoomOut.get("params"))).containsExactly(
                entry("speed", 30.0),
                entry("duration", 0.4));
        assertThat(map(autoRotate.get("params"))).containsExactly(
                entry("enabled", true),
                entry("speed", 20.0));
        assertTarget(zoomIn, "ptz-main", "DUAL_LIGHT_PTZ");
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
        register(object(
                "componentType", "PAYLOAD",
                "code", "warning_light",
                "name", "红蓝警示灯",
                "capabilities", List.of(object(
                        "code", "DEVICE_CONTROL",
                        "actions", List.of(
                                action("GET_LIGHT_STATE"),
                                action("SET_LIGHT_MODE"),
                                action("SET_LIGHT_STATE"))))));

        List<Map<String, Object>> devices = maps(service.controlProfile("robot-001").get("devices"));
        assertThat(devices)
                .filteredOn(device -> "WARNING_LIGHT".equals(device.get("deviceType")))
                .singleElement()
                .satisfies(device -> {
                    assertThat(device)
                            .containsEntry("deviceId", "warning_light")
                            .containsEntry("displayName", "红蓝警示灯")
                            .containsEntry("actions", List.of("get_state", "set_mode", "set_state"));
                    assertThat(map(device.get("controlProfile")))
                            .containsEntry("lightId", "all")
                            .containsEntry("lightIds", List.of("light-001", "light-002", "all"))
                            .containsEntry("modes", List.of(0, 1, 2));
                });

        Map<String, Object> state = publish("warning_light", "set_state", object("enabled", true));
        Map<String, Object> mode = publish("warning_light", "set_mode", object("mode", 2));
        Map<String, Object> query = publish("warning_light", "get_state", object());

        assertThat(map(state.get("params"))).containsExactly(
                entry("lightId", "all"),
                entry("powerOn", true));
        assertThat(map(mode.get("params"))).containsExactly(
                entry("lightId", "all"),
                entry("mode", 2));
        assertThat(map(query.get("params"))).containsExactly(entry("lightId", "all"));
        assertTarget(state, "warning_light", "WARNING_LIGHT");
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

        Map<String, Object> enabled = publish("vehicle-light", "light.vehicle.set", object(
                "front", object("mode", "ON", "brightness", 0),
                "rear", object("mode", "ON", "brightness", 0)));
        Map<String, Object> disabled = publish("vehicle-light", "light.vehicle.set", object(
                "front", object("mode", "OFF", "brightness", 0),
                "rear", object("mode", "OFF", "brightness", 0)));

        assertThat(map(map(enabled.get("params")).get("front"))).containsExactly(
                entry("mode", "ON"),
                entry("brightness", 0));
        assertThat(map(map(enabled.get("params")).get("rear"))).containsExactly(
                entry("mode", "ON"),
                entry("brightness", 0));
        assertThat(map(map(disabled.get("params")).get("front"))).containsExactly(
                entry("mode", "OFF"),
                entry("brightness", 0));
        assertThat(map(map(disabled.get("params")).get("rear"))).containsExactly(
                entry("mode", "OFF"),
                entry("brightness", 0));
        assertTarget(enabled, "vehicle-light", "VEHICLE_LIGHT");
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
    void publishesMultiFunctionAudioTransferWithMediaFileMetadata() {
        register(component("MULTI_FUNCTION_BROADCASTER", "broadcaster-001"));

        Map<String, Object> response = service.publishMultiFunctionAudioTransfer(
                "robot-001",
                "broadcaster-001",
                object(
                        "transferId", "mat-001",
                        "fileId", "file-001",
                        "fileName", "notice.mp3",
                        "fileSize", 10,
                        "orgId", "org001"));

        assertThat(response)
                .containsEntry("status", "PUBLISHED")
                .containsEntry("action", "upload_audio_file");
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(commandPublisher).publishCommand(eq("robot-001"), captor.capture());
        Map<String, Object> payload = map(captor.getValue());
        assertThat(payload)
                .containsEntry("action", "upload_audio_file")
                .containsKey("commandId");
        assertThat(map(payload.get("params")))
                .containsEntry("transferId", "mat-001")
                .containsEntry("fileId", "file-001")
                .containsEntry("fileName", "notice.mp3")
                .containsEntry("fileSize", 10L)
                .containsEntry("orgId", "org001")
                .doesNotContainKeys("downloadUrl", "sha256", "expireAt");
        assertTarget(payload, "broadcaster-001", "MULTI_FUNCTION_BROADCASTER");
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
                        "actions", List.of("up", "down"),
                        "controlProfile", object("maxPanSpeed", 100),
                        "status", object("pan", 0.15, "moving", true)))));

        List<Map<String, Object>> devices = maps(state.get("devices"));
        assertThat(devices).singleElement().satisfies(device -> {
            assertThat(device)
                    .containsEntry("deviceId", "ptz-new-001")
                    .doesNotContainKey("bindingId")
                    .doesNotContainKey("vendor")
                    .containsEntry("deviceType", "DUAL_LIGHT_PTZ");
            assertThat(map(device.get("status")))
                    .containsEntry("pan", 0.15)
                    .containsEntry("moving", true);
                });
    }

    @Test
    void usesManagementIdentityOverClientReportedNameAndType() {
        when(managementClient.deviceTypeName("WHEELED_ROBOT")).thenReturn(Optional.of("轮式机器人"));
        when(managementClient.cachedDeviceBySerialNumber("robot-001")).thenReturn(Optional.of(object(
                "serialNumber", "robot-001",
                "name", "管理端机器人",
                "deviceType", "WHEELED_ROBOT",
                "components", List.of())));
        when(managementClient.deviceBySerialNumber("robot-001")).thenReturn(Optional.of(object(
                "serialNumber", "robot-001",
                "name", "管理端机器人",
                "deviceType", "WHEELED_ROBOT",
                "components", List.of())));

        Map<String, Object> state = service.handleClientState(object(
                "robotId", "robot-001",
                "name", "客户端硬编码名称",
                "type", "轮式机器人",
                "status", "online"));

        assertThat(state)
                .containsEntry("name", "管理端机器人")
                .containsEntry("type", "轮式机器人")
                .containsEntry("typeCode", "WHEELED_ROBOT");
    }

    @Test
    void mapsRobotDogTypeFromManagementProfile() {
        when(managementClient.deviceTypeName("ROBOT_DOG")).thenReturn(Optional.of("机器狗"));
        when(managementClient.cachedDeviceBySerialNumber("robot-dog-001")).thenReturn(Optional.of(object(
                "serialNumber", "robot-dog-001",
                "name", "管理端机器狗",
                "deviceType", "ROBOT_DOG",
                "components", List.of())));

        Map<String, Object> state = service.handleClientState(object(
                "robotId", "robot-dog-001",
                "status", "online"));

        assertThat(state)
                .containsEntry("type", "机器狗")
                .containsEntry("typeCode", "ROBOT_DOG");
    }

    @Test
    void keepsMediaRuntimeWithoutInventingManagementProfileFields() {
        Map<String, Object> state = service.handleClientState(object(
                "robotId", "robot-001",
                "name", "客户端硬编码名称",
                "type", "轮式机器人",
                "status", "online",
                "devices", List.of(object("deviceId", "ptz-001", "deviceType", "DUAL_LIGHT_PTZ"))));

        assertThat(state)
                .containsEntry("robotId", "robot-001")
                .containsEntry("stateSource", "MEDIA_CLIENT_STATUS")
                .doesNotContainKeys("name", "type", "typeCode");
        assertThat(maps(state.get("devices"))).singleElement()
                .satisfies(device -> assertThat(device).containsEntry("deviceId", "ptz-001"));
    }

    @Test
    void marksClientReportedStateAsClientStatusSource() {
        when(managementClient.cachedDeviceBySerialNumber("robot-001")).thenReturn(Optional.of(object(
                "serialNumber", "robot-001",
                "name", "管理端机器人",
                "deviceType", "WHEELED_ROBOT",
                "components", List.of())));

        Map<String, Object> state = service.handleClientState(object(
                "robotId", "robot-001",
                "status", "online"));

        assertThat(state).containsEntry("stateSource", "MEDIA_CLIENT_STATUS");
    }

    @Test
    void exposesRegisteredMultiFunctionAudioUploadAction() {
        register(component(
                "MULTI_FUNCTION_BROADCASTER",
                "broadcaster-001",
                action("UPLOAD_AUDIO_FILE")));

        List<Map<String, Object>> devices = maps(service.controlProfile("robot-001").get("devices"));

        assertThat(devices)
                .filteredOn(device -> "broadcaster-001".equals(device.get("deviceId")))
                .singleElement()
                .satisfies(device -> assertThat(device)
                        .containsEntry("actions", List.of("upload_audio_file")));
    }

    @Test
    void publishesModeChangeWithoutOptimisticallyPublishingRobotState() {
        register(component("BODY", "body"));
        online("手动模式");
        Map<String, Object> session = acquireBase();

        Map<String, Object> response = service.setControlMode("robot-001", object(
                "controlMode", "导航模式",
                "controlSessionId", session.get("controlSessionId"),
                "observedStateSeq", 1), operator());

        assertThat(response)
                .containsEntry("status", "PUBLISHED")
                .containsEntry("controlMode", "手动模式")
                .containsEntry("controlModeName", "手动模式")
                .containsEntry("requestedControlMode", "导航模式")
                .containsEntry("requestedControlModeName", "导航模式")
                .containsEntry("stateSeq", 1L);
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(commandPublisher).publishCommand(eq("robot-001"), captor.capture());
        assertThat(map(captor.getValue()))
                .containsEntry("action", "control.mode.set")
                .containsEntry("params", object("controlMode", "导航模式"));
        verify(webSocketPublisher, never()).publish(eq("robot.state"), any());
    }

    @Test
    void rejectsUnsupportedOrIncompleteModeChanges() {
        register(component("BODY", "base"));
        online("手动模式");
        Map<String, Object> session = acquireBase();

        assertThatThrownBy(() -> service.setControlMode("robot-001", object(
                "controlMode", "ASSISTED",
                "controlSessionId", session.get("controlSessionId"),
                "observedStateSeq", 1), operator()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的控制模式");
        assertThatThrownBy(() -> service.setControlMode("robot-001", object(
                "controlMode", "导航模式"), operator()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("controlSessionId 必填");
    }

    @Test
    void navigationTakeoverAcquiresBaseAndWaitsForRealManualState() {
        register(component("BODY", "base"));
        online("导航模式");

        Map<String, Object> response = service.takeover("robot-001", object(
                "observedStateSeq", 1), operator());

        assertThat(response)
                .containsEntry("status", "ACTIVE")
                .containsEntry("deviceIds", List.of("base"))
                .containsEntry("controlMode", "导航模式")
                .containsEntry("controlModeName", "导航模式")
                .containsEntry("modeChangeStatus", "PUBLISHED")
                .containsEntry("requestedControlMode", "手动模式")
                .containsEntry("requestedControlModeName", "手动模式")
                .containsKey("controlSessionId");
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(commandPublisher).publishCommand(eq("robot-001"), captor.capture());
        assertThat(map(captor.getValue()))
                .containsEntry("action", "control.mode.set")
                .containsEntry("params", object("controlMode", "手动模式"));
        verify(webSocketPublisher, never()).publish(eq("robot.state"), any());
    }

    @Test
    void navigationTakeoverDoesNotStealAnotherTerminalSession() {
        register(component("BODY", "base"));
        online("导航模式");
        acquireBase();
        CurrentUser otherTerminal = new CurrentUser(
                "operator-2",
                "org-1",
                Set.of("EQUIPMENT_OPERATOR"),
                "terminal-2");

        Map<String, Object> response = service.takeover("robot-001", object(
                "observedStateSeq", 1), otherTerminal);

        assertThat(response)
                .containsEntry("code", "CONTROL_LOCKED")
                .containsEntry("message", "target is controlled by another terminal");
        verify(commandPublisher, never()).publishCommand(eq("robot-001"), any());
    }

    @Test
    void mergesEdgeDeviceStatusWithoutClearingExistingRuntimeDevices() {
        register(component("PTZ", "ptz-new-001"));
        service.handleClientState(object(
                "robotId", "robot-001",
                "status", "online",
                "controlMode", "手动模式",
                "devices", List.of(object(
                        "deviceId", "ptz-new-001",
                        "deviceType", "DUAL_LIGHT_PTZ",
                        "status", object("pan", 0.15)))));

        Map<String, Object> state = service.mergeEdgeDeviceStatus("robot-001", object(
                "status", "fault",
                "battery", 47,
                "controlMode", "导航模式",
                "location", object("x", 5.28, "y", 1.37, "z", 0),
                "edgeStatus", object("basic", object("healthStatus", "异常"))));

        assertThat(state)
                .containsEntry("robotId", "robot-001")
                .containsEntry("status", "fault")
                .containsEntry("battery", 47)
                .containsEntry("controlMode", "导航模式")
                .containsEntry("stateSeq", 2L);
        assertThat(maps(state.get("devices")))
                .filteredOn(device -> "ptz-new-001".equals(device.get("deviceId")))
                .singleElement()
                .satisfies(device -> assertThat(map(device.get("status"))).containsEntry("pan", 0.15));
    }

    @Test
    void edgeDeviceStatusDoesNotQueryManagementWithoutRequestIdentity() {
        when(managementClient.cachedDeviceBySerialNumber("m20Pro_01")).thenReturn(Optional.empty());

        Map<String, Object> state = service.mergeEdgeDeviceStatus("m20Pro_01", object(
                "status", "online",
                "battery", 88,
                "edgeStatus", object("basic", object("runningStatus", "待机"))));

        assertThat(state)
                .containsEntry("robotId", "m20Pro_01")
                .containsEntry("battery", 88);
        verify(managementClient, never()).deviceBySerialNumber("m20Pro_01");
    }

    @Test
    void edgeDeviceStatusKeepsManagementTypeWhenPayloadContainsType() {
        when(managementClient.cachedDeviceBySerialNumber("m20Pro_01")).thenReturn(Optional.of(object(
                "serialNumber", "m20Pro_01",
                "name", "管理端 m20Pro",
                "deviceType", "ROBOT_DOG",
                "components", List.of())));
        when(managementClient.deviceTypeName("ROBOT_DOG")).thenReturn(Optional.of("机器狗"));

        Map<String, Object> state = service.mergeEdgeDeviceStatus("m20Pro_01", object(
                "status", "online",
                "battery", 88,
                "type", "机器人",
                "typeCode", "机器人",
                "edgeStatus", object("basic", object("runningStatus", "待机"))));

        assertThat(state)
                .containsEntry("type", "机器狗")
                .containsEntry("typeCode", "ROBOT_DOG")
                .containsEntry("battery", 88);
    }

    @Test
    void keepsFreshEdgeBodyStatusWhenMediaClientHeartbeatArrives() {
        register(component("PTZ", "ptz-new-001"));
        service.handleClientState(object(
                "robotId", "robot-001",
                "status", "online",
                "battery", 90,
                "controlMode", "手动模式",
                "missionStatus", "IDLE"));
        service.mergeEdgeDeviceStatus("robot-001", object(
                "status", "online",
                "battery", 34,
                "controlMode", "导航模式",
                "missionStatus", "COMPLETED",
                "speed", 0.2,
                "location", object("x", 1.2, "y", 3.4)));

        Map<String, Object> state = service.handleClientState(object(
                "robotId", "robot-001",
                "status", "online",
                "battery", 89,
                "controlMode", "手动模式",
                "missionStatus", "IDLE",
                "cameras", List.of(object("cameraId", "camera-001"))));

        assertThat(state)
                .containsEntry("controlMode", "导航模式")
                .containsEntry("controlModeName", "导航模式")
                .containsEntry("battery", 34)
                .containsEntry("missionStatus", "COMPLETED")
                .containsEntry("stateSeq", 2L)
                .containsEntry("speed", 0.2)
                .containsEntry("location", object("x", 1.2, "y", 3.4))
                .containsEntry("clientId", "");
    }

    private Map<String, Object> publish(String deviceId, String action, Map<String, Object> params) {
        Map<String, Object> request = object(
                "target", object("deviceId", deviceId),
                "action", action,
                "params", params,
                "client", object("seq", 7));
        if ("base".equals(deviceId) && "drive.velocity".equals(action)) {
            online("手动模式");
            request.put("controlSessionId", acquireBase().get("controlSessionId"));
        }
        service.publishCommand("robot-001", request, operator());
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(commandPublisher).publishCommand(eq("robot-001"), captor.capture());
        Map<String, Object> payload = map(captor.getValue());
        reset(commandPublisher);
        return payload;
    }

    private void online(String controlMode) {
        service.mergeEdgeDeviceStatus("robot-001", object(
                "status", "online",
                "controlMode", controlMode));
    }

    private Map<String, Object> acquireBase() {
        return service.acquire("robot-001", object(
                "scope", "ROBOT",
                "deviceIds", List.of("base"),
                "actions", List.of("control.mode.set", "drive.velocity")), operator());
    }

    private void register(Map<String, Object> component) {
        Map<String, Object> robot = object(
                "serialNumber", "robot-001",
                "deviceType", "WHEELED_ROBOT",
                "components", List.of(component));
        when(managementClient.deviceBySerialNumber("robot-001")).thenReturn(Optional.of(robot));
        when(managementClient.cachedDeviceBySerialNumber("robot-001")).thenReturn(Optional.of(robot));
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
