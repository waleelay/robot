package com.robot.mediaserver.control.service;

import com.robot.mediaserver.auth.CurrentUser;
import com.robot.mediaserver.config.DateTimeConfig;
import com.robot.mediaserver.control.messaging.EquipmentControlCommandPublisher;
import com.robot.mediaserver.ws.MediaWebSocketPublisher;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class EquipmentControlService {

    private final EquipmentControlCommandPublisher commandPublisher;
    private final MediaWebSocketPublisher webSocketPublisher;
    private final Map<String, Map<String, Object>> sessions = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> robotStates = new ConcurrentHashMap<>();

    public EquipmentControlService(
            EquipmentControlCommandPublisher commandPublisher,
            MediaWebSocketPublisher webSocketPublisher) {
        this.commandPublisher = commandPublisher;
        this.webSocketPublisher = webSocketPublisher;
        fixedRobots().forEach(robot -> robotStates.put(String.valueOf(robot.get("robotId")), defaultRobotState(robot)));
    }

    public List<Map<String, Object>> robots() {
        return fixedRobots().stream()
                .map(robot -> {
                    Map<String, Object> state = robotStates.get(String.valueOf(robot.get("robotId")));
                    Map<String, Object> item = copy(robot);
                    item.put("controlMode", valueOrDefault(state, "controlMode", "MANUAL"));
                    item.put("stateSeq", valueOrDefault(state, "stateSeq", 1));
                    item.put("clientId", valueOrDefault(state, "clientId", item.get("clientId")));
                    item.put("battery", valueOrDefault(state, "battery", item.get("battery")));
                    item.put("lastHeartbeatAt", valueOrDefault(state, "timestamp", item.get("lastHeartbeatAt")));
                    item.put("cameras", valueOrDefault(state, "cameras", item.get("cameras")));
                    item.put("devices", valueOrDefault(state, "devices", item.get("devices")));
                    item.put("status", valueOrDefault(state, "status", "offline"));
                    return item;
                })
                .toList();
    }

    public Map<String, Object> controlProfile(String robotId) {
        Map<String, Object> robot = requireRobot(robotId);
        Map<String, Object> state = robotStates.getOrDefault(robotId, defaultRobotState(robot));
        return object(
                "robotId", robotId,
                "type", robot.get("type"),
                "vendor", robot.get("vendor"),
                "model", robot.get("model"),
                "onlineStatus", valueOrDefault(state, "status", "offline"),
                "controlMode", valueOrDefault(state, "controlMode", "MANUAL"),
                "stateSeq", valueOrDefault(state, "stateSeq", 1),
                "devices", devices(robotId));
    }

    public Map<String, Object> acquire(String robotId, Map<String, Object> request, CurrentUser user) {
        requireRobot(robotId);
        pruneExpiredSessions(robotId);
        List<String> deviceIds = stringList(request.get("deviceIds"));
        String scope = stringValue(request.get("scope"), "DEVICE");
        for (Map<String, Object> session : sessions.values()) {
            if (!robotId.equals(session.get("robotId")) || !"ACTIVE".equals(session.get("status"))) {
                continue;
            }
            if (!conflicts(deviceIds, stringList(session.get("deviceIds")))) {
                continue;
            }
            if (user.clientId().equals(session.get("ownerClientId"))) {
                session.put("leaseExpireAt", OffsetDateTime.now().plusSeconds(30));
                return copy(session);
            }
            if (!user.clientId().equals(session.get("ownerClientId"))) {
                return object(
                        "code", "CONTROL_LOCKED",
                        "message", "target is controlled by another terminal",
                        "holder", session);
            }
        }
        return createSession(robotId, scope, deviceIds, stringList(request.get("actions")), user);
    }

    public Map<String, Object> takeover(String robotId, Map<String, Object> request, CurrentUser user) {
        requireRobot(robotId);
        Map<String, Object> state = robotStates.getOrDefault(robotId, defaultRobotState(requireRobot(robotId)));
        long latestSeq = numberValue(state.get("stateSeq"), 0).longValue();
        long observedSeq = numberValue(request.get("observedStateSeq"), -1).longValue();
        if (observedSeq >= 0 && observedSeq < latestSeq) {
            return object(
                    "code", "ROBOT_STATE_CHANGED",
                    "message", "robot state changed, refresh status before takeover",
                    "latestStateSeq", latestSeq,
                    "latestControlMode", valueOrDefault(state, "controlMode", "MANUAL"));
        }
        List<String> deviceIds = stringList(request.get("deviceIds"));
        Map<String, Object> session = createSession(
                robotId,
                stringValue(request.get("scope"), "ROBOT"),
                deviceIds.isEmpty() ? List.of("base") : deviceIds,
                stringList(request.get("actions")),
                user);
        state.put("controlMode", "MANUAL");
        state.put("missionStatus", "PAUSED");
        state.put("controlOwner", object("userId", user.userId(), "clientId", user.clientId()));
        state.put("stateSeq", latestSeq + 1);
        enrichRobotState(robotId, state);
        robotStates.put(robotId, state);
        webSocketPublisher.publish("robot.state", state);
        session.put("previousMode", stringValue(request.get("fromMode"), "NAVIGATION"));
        session.put("controlMode", "MANUAL");
        session.put("missionStatus", "PAUSED");
        return session;
    }

    public Map<String, Object> heartbeat(String robotId, String controlSessionId) {
        Map<String, Object> session = requireSession(robotId, controlSessionId);
        session.put("leaseExpireAt", OffsetDateTime.now().plusSeconds(30));
        return object(
                "controlSessionId", controlSessionId,
                "status", session.get("status"),
                "leaseExpireAt", session.get("leaseExpireAt"));
    }

    public Map<String, Object> release(String robotId, String controlSessionId, Map<String, Object> request) {
        Map<String, Object> session = requireSession(robotId, controlSessionId);
        session.put("status", "RELEASED");
        session.put("releasedAt", OffsetDateTime.now());
        session.put("reason", request == null ? "user_release" : stringValue(request.get("reason"), "user_release"));
        return object(
                "controlSessionId", controlSessionId,
                "status", "RELEASED",
                "releasedAt", session.get("releasedAt"));
    }

    public List<Map<String, Object>> activeSessions(String robotId) {
        pruneExpiredSessions(robotId);
        return sessions.values().stream()
                .filter(session -> robotId.equals(session.get("robotId")))
                .filter(session -> "ACTIVE".equals(session.get("status")))
                .map(EquipmentControlService::copy)
                .toList();
    }

    public Map<String, Object> confirmToken(String robotId, Map<String, Object> request, CurrentUser user) {
        requireRobot(robotId);
        Map<String, Object> target = mapValue(request.get("target"));
        String action = stringValue(request.get("action"), "");
        return object(
                "confirmToken", "confirm_" + compactUuid(),
                "expiresAt", OffsetDateTime.now().plusSeconds(30),
                "robotId", robotId,
                "target", object(
                        "scope", target.get("scope"),
                        "deviceId", target.get("deviceId")),
                "action", action);
    }

    public Map<String, Object> publishCommand(String robotId, Map<String, Object> request, CurrentUser user) {
        requireRobot(robotId);
        Map<String, Object> mqttPayload = buildMqttPayload(robotId, request, user);
        commandPublisher.publishCommand(robotId, mqttPayload);
        String commandId = "cmd_" + compactUuid();
        Map<String, Object> response = object(
                "commandId", commandId,
                "status", "PUBLISHED",
                "robotId", robotId,
                "target", mqttPayload.get("target"),
                "action", mqttPayload.get("action"),
                "issuedAt", mqttPayload.get("issuedAt"));
        webSocketPublisher.publish("control.command.published", response);
        return response;
    }

    public Map<String, Object> handleClientState(Map<String, Object> payload) {
        String robotId = stringValue(payload.get("robotId"), "");
        if (robotId.isBlank()) {
            return payload;
        }
        Map<String, Object> state = copy(payload);
        state.putIfAbsent("stateSeq", numberValue(state.get("stateSeq"), 1).longValue());
        state.putIfAbsent("status", "offline");
        state.putIfAbsent("controlMode", "MANUAL");
        state.put("timestamp", DateTimeConfig.normalize(state.getOrDefault("timestamp", OffsetDateTime.now())));
        enrichRobotState(robotId, state);
        robotStates.put(robotId, state);
        webSocketPublisher.publish("robot.state", state);
        return state;
    }

    private Map<String, Object> buildMqttPayload(String robotId, Map<String, Object> request, CurrentUser user) {
        Map<String, Object> target = mapValue(request.get("target"));
        Map<String, Object> params = mapValue(request.get("params"));
        Map<String, Object> client = mapValue(request.get("client"));
        Map<String, Object> device = requireDevice(robotId, stringValue(target.get("deviceId"), ""));
        String action = stringValue(request.get("action"), "");
        Map<String, Object> builtParams = buildParams(action, stringValue(target.get("deviceType"), ""), params, device);
        OffsetDateTime now = OffsetDateTime.now();
        return object(
                "robotId", robotId,
                "seq", numberValue(client.get("seq"), 0).longValue(),
                "target", object(
                        "deviceId", target.get("deviceId"),
                        "deviceType", target.get("deviceType")),
                "action", action,
                "params", builtParams,
                "issuedAt", now);
    }

    private Map<String, Object> buildParams(
            String action,
            String deviceType,
            Map<String, Object> params,
            Map<String, Object> device) {
        Map<String, Object> profile = mapValue(device.get("controlProfile"));
        if ("drive.velocity".equals(action)) {
            double maxLinearX = doubleValue(profile.get("maxLinearX"), 1.0);
            double maxLinearY = doubleValue(profile.get("maxLinearY"), 0.0);
            double maxAngularZ = doubleValue(profile.get("maxAngularZ"), 0.8);
            double linearY = clamp(doubleValue(params.get("linearY"), 0.0), -maxLinearY, maxLinearY);
            if ("WHEELED_BASE".equals(deviceType)) {
                linearY = 0.0;
            }
            return object(
                    "linearX", clamp(doubleValue(params.get("linearX"), 0.0), -maxLinearX, maxLinearX),
                    "linearY", linearY,
                    "angularZ", clamp(doubleValue(params.get("angularZ"), 0.0), -maxAngularZ, maxAngularZ));
        }
        if ("ptz.move".equals(action)) {
            double maxPanSpeed = doubleValue(profile.get("maxPanSpeed"), 1.0);
            double maxTiltSpeed = doubleValue(profile.get("maxTiltSpeed"), 1.0);
            return object(
                    "panSpeed", clamp(doubleValue(params.get("panSpeed"), 0.0), -maxPanSpeed, maxPanSpeed),
                    "tiltSpeed", clamp(doubleValue(params.get("tiltSpeed"), 0.0), -maxTiltSpeed, maxTiltSpeed));
        }
        if ("camera.zoom".equals(action)) {
            return object("zoomSpeed", clamp(doubleValue(params.get("zoomSpeed"), 0.0), -1.0, 1.0));
        }
        if ("ptz.auto_rotate".equals(action)) {
            double maxPanSpeed = doubleValue(profile.get("maxPanSpeed"), 1.0);
            return object(
                    "enabled", booleanValue(params.get("enabled"), false),
                    "panSpeed", clamp(doubleValue(params.get("panSpeed"), 0.3), 0.0, maxPanSpeed));
        }
        if (action.startsWith("volume.")) {
            return object(
                    "volume", clampedInt(params.get("volume"), 50, 0, 100),
                    "step", clampedInt(params.get("step"), 5, 1, 100),
                    "muted", booleanValue(params.get("muted"), false));
        }
        if ("light.set".equals(action)) {
            return object(
                    "enabled", booleanValue(params.get("enabled"), false),
                    "brightness", clamp(doubleValue(params.get("brightness"), 100.0), 0.0, 100.0),
                    "mode", stringValue(params.get("mode"), "STEADY"));
        }
        if ("light.vehicle.set".equals(action)) {
            Map<String, Object> front = mapValue(params.get("front"));
            Map<String, Object> rear = mapValue(params.get("rear"));
            int frontMode = vehicleLightMode(front);
            int rearMode = vehicleLightMode(rear);
            Map<String, Object> frontStatus = vehicleLightPart(front, frontMode);
            Map<String, Object> rearStatus = vehicleLightPart(rear, rearMode);
            return object(
                    "front", frontStatus,
                    "rear", rearStatus,
                    "op", "publish",
                    "topic", "/robot_light_ctl",
                    "type", "robot_status_core/RobotLightCmd",
                    "msg", object(
                            "front_mode", frontMode,
                            "front_custom_value", frontMode == 3 ? frontStatus.get("customValue") : 0,
                            "rear_mode", rearMode,
                            "rear_custom_value", rearMode == 3 ? rearStatus.get("customValue") : 0));
        }
        if ("payload.fire".equals(action)) {
            return object("channel", numberValue(params.get("channel"), 1).intValue());
        }
        if ("payload.safety_switch".equals(action)) {
            return object("enabled", booleanValue(params.get("enabled"), false));
        }
        return copy(params);
    }

    private Map<String, Object> createSession(
            String robotId,
            String scope,
            List<String> deviceIds,
            List<String> actions,
            CurrentUser user) {
        String sessionId = "tc_" + compactUuid();
        Map<String, Object> session = object(
                "controlSessionId", sessionId,
                "robotId", robotId,
                "ownerUserId", user.userId(),
                "ownerClientId", user.clientId(),
                "scope", scope,
                "deviceIds", deviceIds,
                "actions", actions,
                "mode", "EXCLUSIVE",
                "status", "ACTIVE",
                "leaseExpireAt", OffsetDateTime.now().plusSeconds(30));
        sessions.put(sessionId, session);
        return session;
    }

    private Map<String, Object> requireSession(String robotId, String controlSessionId) {
        Map<String, Object> session = sessions.get(controlSessionId);
        if (session == null || !robotId.equals(session.get("robotId"))) {
            throw new IllegalArgumentException("Control session not found: " + controlSessionId);
        }
        return session;
    }

    private void pruneExpiredSessions(String robotId) {
        OffsetDateTime now = OffsetDateTime.now();
        sessions.entrySet().removeIf(entry -> {
            Map<String, Object> session = entry.getValue();
            return robotId.equals(session.get("robotId"))
                    && "ACTIVE".equals(session.get("status"))
                    && isExpired(session, now);
        });
    }

    private boolean isExpired(Map<String, Object> session, OffsetDateTime now) {
        OffsetDateTime leaseExpireAt = offsetDateTimeValue(session.get("leaseExpireAt"));
        if (leaseExpireAt == null) {
            return false;
        }
        return !leaseExpireAt.isAfter(now);
    }

    private Map<String, Object> requireRobot(String robotId) {
        return fixedRobots().stream()
                .filter(robot -> robotId.equals(robot.get("robotId")))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Robot not found: " + robotId));
    }

    private Map<String, Object> requireDevice(String robotId, String deviceId) {
        return devices(robotId).stream()
                .filter(device -> deviceId.equals(device.get("deviceId")))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Device not bound: " + deviceId));
    }

    private static boolean conflicts(List<String> requested, List<String> existing) {
        if (requested.isEmpty() || existing.isEmpty()) {
            return true;
        }
        return requested.stream().anyMatch(existing::contains);
    }

    private static Map<String, Object> defaultRobotState(Map<String, Object> robot) {
        return object(
                "robotId", robot.get("robotId"),
                "controlMode", "MANUAL",
                "stateSeq", 1,
                "missionStatus", "IDLE",
                "navigationStatus", "IDLE",
                "controlOwner", null,
                "estopActive", false,
                "devices", devices(String.valueOf(robot.get("robotId"))),
                "status", "offline",
                "timestamp", OffsetDateTime.now());
    }

    private static void enrichRobotState(String robotId, Map<String, Object> state) {
        if (!stringValue(state.get("type"), "").isBlank()) {
            return;
        }
        fixedRobots().stream()
                .filter(robot -> robotId.equals(robot.get("robotId")))
                .findFirst()
                .map(robot -> robot.get("type"))
                .ifPresent(type -> state.put("type", type));
    }

    private static List<Map<String, Object>> fixedRobots() {
        return List.of(
                object(
                        "robotId", "robot-001",
                        "clientId", "robot-client-songling-001",
                        "name", "R1轮式机器人",
                        "type", "轮式机器人",
                        "vendor", "SONGLING",
                        "model", "SCOUT",
                        "status", "offline",
                        "battery", 86,
                        "lastHeartbeatAt", OffsetDateTime.now(),
                        "devices", devices("robot-001"),
                        "cameras", List.of(
                                camera("camera01", "dual_gimbal", "云台-可见光"),
                                camera("camera02", "dual_gimbal", "云台-热成像"),
                                camera("camera03", "body", "本体相机"))),
                object(
                        "robotId", "robot-002",
                        "clientId", "robot-client-deep-001",
                        "name", "G1四足机器人",
                        "type", "四足机器狗",
                        "vendor", "DEEPNROBOTICS",
                        "model", "X30",
                        "status", "offline",
                        "battery", 78,
                        "lastHeartbeatAt", OffsetDateTime.now(),
                        "devices", devices("robot-002"),
                        "cameras", List.of(camera("camera04", "dual_gimbal", "头部双光云台"))),
                object(
                        "robotId", "robot-unitree-001",
                        "clientId", "robot-client-unitree-001",
                        "name", "G2四足机器人",
                        "type", "四足机器狗",
                        "vendor", "UNITREE",
                        "model", "B2",
                        "status", "offline",
                        "battery", 92,
                        "lastHeartbeatAt", OffsetDateTime.now(),
                        "devices", devices("robot-unitree-001"),
                        "cameras", List.of(camera("camera07", "dual_gimbal", "双光云台"))));
    }

    private static Map<String, Object> camera(String deviceId, String groupType, String name) {
        return object(
                "cameraId", deviceId,
                "deviceId", deviceId,
                "groupType", groupType,
                "name", name,
                "quality", "sub");
    }

    private static List<Map<String, Object>> devices(String robotId) {
        if ("robot-002".equals(robotId)) {
            return List.of(
                    base("QUADRUPED_BASE", "DEEPNROBOTICS", "X30", 0.8, 0.4, 0.6),
                    ptz(),
                    audioControl(),
                    launcher(),
                    netGun(),
                    warningLight("warning-light-left", "左警示灯"),
                    warningLight("warning-light-right", "右警示灯"),
                    vehicleLight(),
                    intercom());
        }
        if ("robot-unitree-001".equals(robotId)) {
            return List.of(
                    base("QUADRUPED_BASE", "UNITREE", "B2", 0.8, 0.4, 0.6),
                    ptz(),
                    audioControl(),
                    warningLight("warning-light-left", "左警示灯"),
                    warningLight("warning-light-right", "右警示灯"),
                    vehicleLight(),
                    object(
                            "deviceId", "searchlight-001",
                            "bindingId", "bind-robot-unitree-001-searchlight-001",
                            "scope", "PAYLOAD",
                            "deviceType", "SEARCHLIGHT",
                            "displayName", "探照灯",
                            "vendor", "CUSTOM",
                            "model", "SL-01",
                            "onlineStatus", "offline",
                            "controlStatus", "idle",
                            "enabled", true,
                            "actions", List.of("light.set"),
                            "controlProfile", object("maxBrightness", 100)));
        }
        return List.of(
                base("WHEELED_BASE", "SONGLING", "SCOUT", 1.0, 0.4, 0.8),
                ptz(),
                audioControl(),
                launcher(),
                netGun(),
                warningLight("warning-light-left", "左警示灯"),
                warningLight("warning-light-right", "右警示灯"),
                vehicleLight(),
                intercom());
    }

    private static Map<String, Object> base(
            String deviceType,
            String vendor,
            String model,
            double maxLinearX,
            double maxLinearY,
            double maxAngularZ) {
        return object(
                "deviceId", "base",
                "bindingId", "bind-base",
                "scope", "BODY",
                "deviceType", deviceType,
                "displayName", "机器人本体",
                "vendor", vendor,
                "model", model,
                "onlineStatus", "offline",
                "controlStatus", "idle",
                "enabled", true,
                "actions", List.of("drive.velocity", "navigation.return_home", "docking.leave"),
                "controlProfile", object(
                        "maxLinearX", maxLinearX,
                        "maxLinearY", maxLinearY,
                        "maxAngularZ", maxAngularZ,
                        "controlFrameRateHz", 10));
    }

    private static Map<String, Object> ptz() {
        return object(
                "deviceId", "ptz-dual-001",
                "bindingId", "bind-ptz-dual-001",
                "scope", "PAYLOAD",
                "deviceType", "DUAL_LIGHT_PTZ",
                "displayName", "双光云台",
                "vendor", "CUSTOM",
                "model", "DL-PTZ-01",
                "onlineStatus", "offline",
                "controlStatus", "idle",
                "enabled", true,
                "actions", List.of("ptz.move", "ptz.auto_rotate", "ptz.home", "camera.zoom"),
                "status", object(
                        "autoRotateEnabled", false,
                        "panSpeed", 0),
                "controlProfile", object(
                        "maxPanSpeed", 1.0,
                        "maxTiltSpeed", 1.0,
                        "controlFrameRateHz", 10));
    }

    private static Map<String, Object> buildNetGun() {
        return object(
                "deviceId", "net-gun-001",
                "bindingId", "bind-net-gun-001",
                "scope", "PAYLOAD",
                "deviceType", "NET_GUN",
                "displayName", "捕网枪",
                "vendor", "CUSTOM",
                "model", "NL-01",
                "onlineStatus", "offline",
                "controlStatus", "idle",
                "enabled", true,
                "riskLevel", "HIGH",
                "actions", List.of("payload.fire"),
                "controlProfile", object(
                        "requiresConfirm", true,
                        "cooldownMs", 3000));
    }

    private static Map<String, Object> netGun() {
        return buildNetGun();
    }

    private static Map<String, Object> launcher() {
        return object(
                "deviceId", "launcher-001",
                "bindingId", "bind-launcher-001",
                "scope", "PAYLOAD",
                "deviceType", "LAUNCHER",
                "displayName", "六联发射器",
                "vendor", "CUSTOM",
                "model", "LCH-06",
                "onlineStatus", "offline",
                "controlStatus", "idle",
                "enabled", true,
                "riskLevel", "HIGH",
                "actions", List.of("payload.safety_switch", "payload.fire"),
                "controlProfile", object(
                        "channels", List.of(1, 2, 3, 4, 5, 6),
                        "requiresConfirm", true,
                        "requiresSafetySwitch", true));
    }

    private static Map<String, Object> warningLight(String deviceId, String displayName) {
        return object(
                "deviceId", deviceId,
                "bindingId", "bind-" + deviceId,
                "scope", "PAYLOAD",
                "deviceType", "WARNING_LIGHT",
                "displayName", displayName,
                "vendor", "CUSTOM",
                "model", "WL-01",
                "onlineStatus", "offline",
                "controlStatus", "idle",
                "enabled", true,
                "actions", List.of("light.warning.set"),
                "status", object("enabled", false),
                "controlProfile", object("modes", List.of("ON", "OFF")));
    }

    private static Map<String, Object> vehicleLight() {
        return object(
                "deviceId", "vehicle-light",
                "bindingId", "bind-vehicle-light",
                "scope", "PAYLOAD",
                "deviceType", "VEHICLE_LIGHT",
                "displayName", "车灯光",
                "vendor", "CUSTOM",
                "model", "VL-01",
                "onlineStatus", "offline",
                "controlStatus", "idle",
                "enabled", true,
                "actions", List.of("light.vehicle.set"),
                "controlProfile", object(
                        "parts", List.of("front", "rear"),
                        "modes", List.of("OFF", "ON", "BREATH", "CUSTOM"),
                        "modeMapping", object("OFF", 0, "ON", 1, "BREATH", 2, "CUSTOM", 3),
                        "maxBrightness", 100,
                        "rosTopic", "/robot_light_ctl",
                        "rosType", "robot_status_core/RobotLightCmd"));
    }

    private static Map<String, Object> audioControl() {
        return object(
                "deviceId", "audio-control-001",
                "bindingId", "bind-audio-control-001",
                "scope", "AUDIO",
                "deviceType", "CLIENT_AUDIO",
                "displayName", "客户端音量",
                "onlineStatus", "offline",
                "controlStatus", "idle",
                "enabled", true,
                "actions", List.of("volume.set", "volume.up", "volume.down", "volume.mute"),
                "status", object("volume", 50, "muted", false),
                "controlProfile", object("step", 5, "minVolume", 0, "maxVolume", 100));
    }

    private static Map<String, Object> intercom() {
        return object(
                "deviceId", "intercom-001",
                "bindingId", "bind-intercom-001",
                "scope", "AUDIO",
                "deviceType", "INTERCOM",
                "displayName", "语音对讲",
                "onlineStatus", "offline",
                "controlStatus", "idle",
                "enabled", true,
                "actions", List.of("volume.set", "volume.up", "volume.down", "volume.mute"),
                "status", object("volume", 50, "muted", false));
    }

    private static String compactUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> map ? new LinkedHashMap<>((Map<String, Object>) map) : new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return new ArrayList<>();
        }
        return list.stream().map(String::valueOf).toList();
    }

    private static String stringValue(Object value, String defaultValue) {
        return value == null || String.valueOf(value).isBlank() ? defaultValue : String.valueOf(value);
    }

    private static Number numberValue(Object value, Number defaultValue) {
        return value instanceof Number number ? number : defaultValue;
    }

    private static double doubleValue(Object value, double defaultValue) {
        return value instanceof Number number ? number.doubleValue() : defaultValue;
    }

    private static boolean booleanValue(Object value, boolean defaultValue) {
        return value instanceof Boolean bool ? bool : defaultValue;
    }

    private static OffsetDateTime offsetDateTimeValue(Object value) {
        if (value instanceof OffsetDateTime time) {
            return time;
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return DateTimeConfig.parseOffsetDateTime(text);
            } catch (DateTimeParseException ex) {
                return null;
            }
        }
        return null;
    }

    private static int vehicleLightMode(Map<String, Object> part) {
        Object modeCode = part.get("modeCode");
        if (modeCode instanceof Number number) {
            return Math.max(0, Math.min(3, number.intValue()));
        }
        return switch (stringValue(part.get("mode"), "OFF").toUpperCase()) {
            case "ON" -> 1;
            case "BREATH" -> 2;
            case "CUSTOM" -> 3;
            default -> 0;
        };
    }

    private static Map<String, Object> vehicleLightPart(Map<String, Object> part, int modeCode) {
        int customValue = modeCode == 3 ? clampedInt(part.get("customValue"), 0, 0, 100) : 0;
        return object(
                "mode", vehicleLightModeName(modeCode),
                "modeCode", modeCode,
                "customValue", customValue);
    }

    private static String vehicleLightModeName(int modeCode) {
        return switch (modeCode) {
            case 1 -> "ON";
            case 2 -> "BREATH";
            case 3 -> "CUSTOM";
            default -> "OFF";
        };
    }

    private static int clampedInt(Object value, int defaultValue, int min, int max) {
        int number = value instanceof Number item ? item.intValue() : defaultValue;
        return Math.max(min, Math.min(max, number));
    }

    private static Object valueOrDefault(Map<String, Object> map, String key, Object defaultValue) {
        Object value = map.get(key);
        return value == null ? defaultValue : value;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Map<String, Object> copy(Map<String, Object> source) {
        return new LinkedHashMap<>(source);
    }

    private static Map<String, Object> object(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length - 1; i += 2) {
            map.put(String.valueOf(values[i]), values[i + 1]);
        }
        return map;
    }
}
