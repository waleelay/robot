package com.robot.control.service;

import com.robot.control.auth.CurrentUser;
import com.robot.control.config.DateTimeConfig;
import com.robot.control.messaging.EquipmentControlCommandPublisher;
import com.robot.control.ws.MediaWebSocketPublisher;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * 装备控制会话和命令编排服务。
 *
 * @author leelay
 * @date 2026-07-05
 */
@Service
public class EquipmentControlService {

    private final EquipmentControlCommandPublisher commandPublisher;
    private final MediaWebSocketPublisher webSocketPublisher;
    private final Map<String, Map<String, Object>> sessions = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> robotStates = new ConcurrentHashMap<>();

    /**
     * 创建 EquipmentControlService 实例。
     *
     * @param commandPublisher commandPublisher
     * @param webSocketPublisher webSocketPublisher
     */
    public EquipmentControlService(
            EquipmentControlCommandPublisher commandPublisher,
            MediaWebSocketPublisher webSocketPublisher) {
        this.commandPublisher = commandPublisher;
        this.webSocketPublisher = webSocketPublisher;
        fixedRobots().forEach(robot -> robotStates.put(String.valueOf(robot.get("robotId")), defaultRobotState(robot)));
    }

    /**
     * 查询机器人控制画像。
     *
     * @param robotId 机器人 ID
     * @return 机器人控制画像
     */
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

    /**
     * 申请控制会话。
     *
     * @param robotId 机器人 ID
     * @param request 请求参数
     * @param user 当前用户
     * @return 控制会话信息
     */
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

    /**
     * 接管控制会话。
     *
     * @param robotId 机器人 ID
     * @param request 请求参数
     * @param user 当前用户
     * @return 控制会话信息
     */
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

    /**
     * 设置机器人控制模式。
     *
     * @param robotId 机器人 ID
     * @param request 请求参数
     * @param user 当前用户
     * @return 控制模式设置结果
     */
    public Map<String, Object> setControlMode(String robotId, Map<String, Object> request, CurrentUser user) {
        requireRobot(robotId);
        String controlMode = normalizeControlMode(stringValue(request.get("controlMode"), ""));
        Map<String, Object> state = robotStates.getOrDefault(robotId, defaultRobotState(requireRobot(robotId)));
        long latestSeq = numberValue(state.get("stateSeq"), 0).longValue();
        OffsetDateTime now = OffsetDateTime.now();
        Map<String, Object> base = requireDevice(robotId, "base");
        Map<String, Object> mqttPayload = object(
                "robotId", robotId,
                "seq", latestSeq + 1,
                "target", object(
                        "deviceId", "base",
                        "deviceType", base.get("deviceType")),
                "action", "control.mode.set",
                "params", object("controlMode", controlMode),
                "issuedAt", now.toString());
        commandPublisher.publishCommand(robotId, mqttPayload);
        state.put("controlMode", controlMode);
        state.put("missionStatus", missionStatusForMode(controlMode));
        state.put("navigationStatus", navigationStatusForMode(controlMode));
        state.put("controlOwner", object("userId", user.userId(), "clientId", user.clientId()));
        state.put("stateSeq", latestSeq + 1);
        state.put("timestamp", now.toString());
        enrichRobotState(robotId, state);
        robotStates.put(robotId, state);
        webSocketPublisher.publish("robot.state", state);
        return object(
                "status", "PUBLISHED",
                "robotId", robotId,
                "controlMode", controlMode,
                "stateSeq", state.get("stateSeq"),
                "issuedAt", now.toString());
    }

    /**
     * 释放控制会话。
     *
     * @param robotId 机器人 ID
     * @param controlSessionId 控制会话 ID
     * @param request 请求参数
     * @return 释放结果
     */
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

    /**
     * 生成高风险控制确认 Token。
     *
     * @param robotId 机器人 ID
     * @param request 请求参数
     * @param user 当前用户
     * @return 确认 Token 信息
     */
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

    /**
     * 发布设备控制命令。
     *
     * @param robotId 机器人 ID
     * @param request 请求参数
     * @param user 当前用户
     * @return 命令发布结果
     */
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

    /**
     * 处理机器人客户端状态载荷。
     *
     * @param payload 消息载荷
     * @return 客户端状态处理结果
     */
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

    /**
     * 构建设备控制 MQTT 载荷。
     *
     * @param robotId 机器人 ID
     * @param request 请求参数
     * @param user 当前用户
     * @return MQTT 载荷
     */
    private Map<String, Object> buildMqttPayload(String robotId, Map<String, Object> request, CurrentUser user) {
        Map<String, Object> target = mapValue(request.get("target"));
        Map<String, Object> params = mapValue(request.get("params"));
        Map<String, Object> client = mapValue(request.get("client"));
        Map<String, Object> device = requireDevice(robotId, stringValue(target.get("deviceId"), ""));
        String action = stringValue(request.get("action"), "");
        if (!stringList(device.get("actions")).contains(action)) {
            throw new IllegalArgumentException("设备不支持该动作：" + action);
        }
        String deviceType = stringValue(device.get("deviceType"), stringValue(target.get("deviceType"), ""));
        Map<String, Object> builtParams = buildParams(action, deviceType, params, device);
        OffsetDateTime now = OffsetDateTime.now();
        return object(
                "robotId", robotId,
                "seq", numberValue(client.get("seq"), 0).longValue(),
                "target", object(
                        "deviceId", target.get("deviceId"),
                        "deviceType", deviceType),
                "action", action,
                "params", builtParams,
                "issuedAt", now);
    }

    /**
     * 构建设备动作参数。
     *
     * @param action 动作名称
     * @param deviceType deviceType
     * @param params params
     * @param device device
     * @return 设备动作参数
     */
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
        if ("DUAL_LIGHT_PTZ".equals(deviceType) && isPtzDirectionAction(action)) {
            return object(
                    "speed", clamp(doubleValue(params.get("speed"), 20.0), 0.1, 100.0),
                    "duration", clamp(doubleValue(params.get("duration"), 0.3), 0.05, 5.0));
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
        if ("control.mode.set".equals(action)) {
            return object("controlMode", normalizeControlMode(stringValue(params.get("controlMode"), "MANUAL")));
        }
        if (isSpeakerDeviceType(deviceType) && "set_volume".equals(action)) {
            return object(
                    "volumePercent", clampedInt(valueOrDefault(params, "volumePercent", params.get("volume")), 50, 0, 100));
        }
        if (isSpeakerDeviceType(deviceType) && "set_mute".equals(action)) {
            return object("mute", booleanValue(valueOrDefault(params, "mute", params.get("muted")), false));
        }
        if ("WARNING_LIGHT".equals(deviceType) && "get_state".equals(action)) {
            return object("lightId", warningLightId(params, device));
        }
        if ("WARNING_LIGHT".equals(deviceType) && "set_state".equals(action)) {
            return object(
                    "lightId", warningLightId(params, device),
                    "powerOn", booleanValue(valueOrDefault(params, "powerOn", params.get("enabled")), false));
        }
        if ("WARNING_LIGHT".equals(deviceType) && "set_mode".equals(action)) {
            return object(
                    "lightId", warningLightId(params, device),
                    "mode", clampedInt(params.get("mode"), 0, 0, 2));
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
            return object(
                    "front", vehicleLightPart(front),
                    "rear", vehicleLightPart(rear));
        }
        if ("LAUNCHER".equals(deviceType) && "get_status".equals(action)) {
            return object(
                    "temporarilyEnableSafety", booleanValue(params.get("temporarilyEnableSafety"), true),
                    "restoreSafetyAfterQuery", booleanValue(params.get("restoreSafetyAfterQuery"), true));
        }
        if ("LAUNCHER".equals(deviceType) && "set_safety".equals(action)) {
            return object(
                    "safetyOn", booleanValue(params.get("safetyOn"), booleanValue(params.get("enabled"), false)),
                    "waitStatus", booleanValue(params.get("waitStatus"), true));
        }
        if ("LAUNCHER".equals(deviceType) && "fire".equals(action)) {
            return object(
                    "tube", clampedInt(valueOrDefault(params, "tube", params.get("channel")), 1, 1, 6),
                    "waitStatusAfterFire", booleanValue(params.get("waitStatusAfterFire"), true),
                    "keepSafetyOn", booleanValue(params.get("keepSafetyOn"), false));
        }
        if (("NET_GUN".equals(deviceType) || "NET_LAUNCHER".equals(deviceType)) && "fire".equals(action)) {
            return object();
        }
        return copy(params);
    }

    /**
     * 创建控制会话快照。
     *
     * @param robotId 机器人 ID
     * @param scope scope
     * @param deviceIds deviceIds
     * @param actions actions
     * @param user 当前用户
     * @return 控制会话快照
     */
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

    /**
     * 获取并校验控制会话。
     *
     * @param robotId 机器人 ID
     * @param controlSessionId 控制会话 ID
     * @return 控制会话
     */
    private Map<String, Object> requireSession(String robotId, String controlSessionId) {
        Map<String, Object> session = sessions.get(controlSessionId);
        if (session == null || !robotId.equals(session.get("robotId"))) {
            throw new IllegalArgumentException("未找到控制会话：" + controlSessionId);
        }
        return session;
    }

    /**
     * 清理指定机器人的过期控制会话。
     *
     * @param robotId 机器人 ID
     */
    private void pruneExpiredSessions(String robotId) {
        OffsetDateTime now = OffsetDateTime.now();
        sessions.entrySet().removeIf(entry -> {
            Map<String, Object> session = entry.getValue();
            return robotId.equals(session.get("robotId"))
                    && "ACTIVE".equals(session.get("status"))
                    && isExpired(session, now);
        });
    }

    /**
     * 判断控制会话是否过期。
     *
     * @param session WebSocket 会话
     * @param now now
     * @return 是否过期
     */
    private boolean isExpired(Map<String, Object> session, OffsetDateTime now) {
        OffsetDateTime leaseExpireAt = offsetDateTimeValue(session.get("leaseExpireAt"));
        if (leaseExpireAt == null) {
            return false;
        }
        return !leaseExpireAt.isAfter(now);
    }

    /**
     * 获取并校验机器人状态。
     *
     * @param robotId 机器人 ID
     * @return 机器人状态
     */
    private Map<String, Object> requireRobot(String robotId) {
        return fixedRobots().stream()
                .filter(robot -> robotId.equals(robot.get("robotId")))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未找到机器人：" + robotId));
    }

    /**
     * 获取并校验机器人设备。
     *
     * @param robotId 机器人 ID
     * @param deviceId 设备 ID
     * @return 设备信息
     */
    private Map<String, Object> requireDevice(String robotId, String deviceId) {
        return devices(robotId).stream()
                .filter(device -> deviceId.equals(device.get("deviceId")))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("设备未绑定：" + deviceId));
    }

    /**
     * 判断控制范围是否冲突。
     *
     * @param requested 请求范围
     * @param existing 已有范围
     * @return 是否冲突
     */
    private static boolean conflicts(List<String> requested, List<String> existing) {
        if (requested.isEmpty() || existing.isEmpty()) {
            return true;
        }
        return requested.stream().anyMatch(existing::contains);
    }

    /**
     * 构造默认机器人状态。
     *
     * @param robot 机器人配置
     * @return 默认机器人状态
     */
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

    /**
     * 补齐机器人状态的派生字段。
     *
     * @param robotId 机器人 ID
     * @param state 机器人状态
     */
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

    /**
     * 构造本地固定机器人数据。
     *
     * @return 固定机器人列表
     */
    private static List<Map<String, Object>> fixedRobots() {
        return List.of(
                object(
                        "robotId", "test001",
                        "clientId", "robot-client-songling-001",
                        "name", "R1轮式机器人",
                        "type", "轮式机器人",
                        "vendor", "SONGLING",
                        "model", "SCOUT",
                        "status", "offline",
                        "battery", 86,
                        "lastHeartbeatAt", OffsetDateTime.now(),
                        "devices", devices("test001"),
                        "cameras", List.of(
                                camera("camera01", "dual_gimbal", "云台-可见光"),
                                camera("camera02", "dual_gimbal", "云台-热成像"),
                                camera("camera03", "body", "本体相机"))),
                object(
                        "robotId", "test002",
                        "clientId", "robot-client-deep-001",
                        "name", "G1四足机器狗",
                        "type", "四足机器狗",
                        "vendor", "DEEPNROBOTICS",
                        "model", "X30",
                        "status", "offline",
                        "battery", 78,
                        "lastHeartbeatAt", OffsetDateTime.now(),
                        "devices", devices("test002"),
                        "cameras", List.of(camera("camera04", "dual_gimbal", "头部双光云台"))),
                object(
                        "robotId", "robot-unitree-001",
                        "clientId", "robot-client-unitree-001",
                        "name", "G2四足机器狗",
                        "type", "四足机器狗",
                        "vendor", "UNITREE",
                        "model", "B2",
                        "status", "offline",
                        "battery", 92,
                        "lastHeartbeatAt", OffsetDateTime.now(),
                        "devices", devices("robot-unitree-001"),
                        "cameras", List.of(camera("camera07", "dual_gimbal", "双光云台"))));
    }

    /**
     * 构造摄像头描述。
     *
     * @param deviceId 设备 ID
     * @param groupType groupType
     * @param name 名称
     * @return 摄像头信息
     */
    private static Map<String, Object> camera(String deviceId, String groupType, String name) {
        return object(
                "cameraId", deviceId,
                "deviceId", deviceId,
                "groupType", groupType,
                "name", name,
                "quality", "sub");
    }

    /**
     * 构造机器人设备能力列表。
     *
     * @param robotId 机器人 ID
     * @return 设备能力列表
     */
    private static List<Map<String, Object>> devices(String robotId) {
        if ("test002".equals(robotId)) {
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

    /**
     * 构造底盘控制能力。
     *
     * @param deviceType deviceType
     * @param vendor vendor
     * @param model model
     * @param maxLinearX maxLinearX
     * @param maxLinearY maxLinearY
     * @param maxAngularZ maxAngularZ
     * @return 底盘控制能力
     */
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

    /**
     * 构造云台设备能力。
     *
     * @return 云台能力
     */
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
                "actions", List.of(
                        "up", "down", "left", "right",
                        "left_up", "right_up", "left_down", "right_down",
                        "ptz.auto_rotate", "ptz.home", "camera.zoom"),
                "status", object(
                        "autoRotateEnabled", false,
                        "panSpeed", 0),
                "controlProfile", object(
                        "maxPanSpeed", 1.0,
                        "maxTiltSpeed", 1.0,
                        "controlFrameRateHz", 10));
    }

    /**
     * 构造网枪设备能力。
     *
     * @return 网枪能力
     */
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
                "actions", List.of("fire"),
                "controlProfile", object(
                        "requiresConfirm", true,
                        "cooldownMs", 3000));
    }

    /**
     * 构造网枪发射部件。
     *
     * @return 网枪部件
     */
    private static Map<String, Object> netGun() {
        return buildNetGun();
    }

    /**
     * 构造发射器部件。
     *
     * @return 发射器部件
     */
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
                "actions", List.of("get_status", "set_safety", "fire"),
                "controlProfile", object(
                        "tubes", List.of(1, 2, 3, 4, 5, 6),
                        "requiresConfirm", true,
                        "requiresSafetySwitch", true));
    }

    /**
     * 构造警示灯设备能力。
     *
     * @param deviceId 设备 ID
     * @param displayName displayName
     * @return 警示灯能力
     */
    private static Map<String, Object> warningLight(String deviceId, String displayName) {
        String lightId = switch (deviceId) {
            case "warning-light-right" -> "light-002";
            case "warning-light-all" -> "all";
            default -> "light-001";
        };
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
                "actions", List.of("get_state", "set_state", "set_mode"),
                "status", object("enabled", false, "powerOn", false, "mode", 0, "online", true),
                "controlProfile", object(
                        "lightId", lightId,
                        "lightIds", List.of("light-001", "light-002", "all"),
                        "modes", List.of(0, 1, 2),
                        "supportsAll", true));
    }

    /**
     * 构造车灯设备能力。
     *
     * @return 车灯能力
     */
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
                        "maxBrightness", 100));
    }

    /**
     * 构造音频控制能力。
     *
     * @return 音频控制能力
     */
    private static Map<String, Object> audioControl() {
        return object(
                "deviceId", "audio-control-001",
                "bindingId", "bind-audio-control-001",
                "scope", "AUDIO",
                "deviceType", "SPEAKER",
                "displayName", "扬声器",
                "onlineStatus", "offline",
                "controlStatus", "idle",
                "enabled", true,
                "actions", List.of("set_volume", "set_mute"),
                "status", object("volume", 50, "volumePercent", 50, "muted", false),
                "controlProfile", object("step", 5, "minVolume", 0, "maxVolume", 100));
    }

    /**
     * 构造对讲能力。
     *
     * @return 对讲能力
     */
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
                "actions", List.of("set_volume", "set_mute"),
                "status", object("volume", 50, "volumePercent", 50, "muted", false));
    }

    /**
     * 生成紧凑型 UUID。
     *
     * @return 紧凑 UUID
     */
    private static String compactUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 将对象转换为 Map。
     *
     * @param value 待处理值
     * @return Map 值
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> map ? new LinkedHashMap<>((Map<String, Object>) map) : new LinkedHashMap<>();
    }

    /**
     * 将对象转换为字符串列表。
     *
     * @param value 待处理值
     * @return 字符串列表
     */
    @SuppressWarnings("unchecked")
    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return new ArrayList<>();
        }
        return list.stream().map(String::valueOf).toList();
    }

    /**
     * 读取字符串值并应用默认值。
     *
     * @param value 待处理值
     * @param defaultValue 默认值
     * @return 字符串值
     */
    private static String stringValue(Object value, String defaultValue) {
        return value == null || String.valueOf(value).isBlank() ? defaultValue : String.valueOf(value);
    }

    /**
     * 规范化控制模式。
     *
     * @param value 待处理值
     * @return 规范化控制模式
     */
    private static String normalizeControlMode(String value) {
        String mode = stringValue(value, "MANUAL").toUpperCase();
        if (List.of("MANUAL", "ASSISTED", "NAVIGATION").contains(mode)) {
            return mode;
        }
        throw new IllegalArgumentException("不支持的控制模式：" + value);
    }

    /**
     * 判断是否为双光云台方向动作。
     *
     * @param action 动作名
     * @return true 表示方向动作
     */
    private static boolean isPtzDirectionAction(String action) {
        return List.of(
                "up", "down", "left", "right",
                "left_up", "right_up", "left_down", "right_down").contains(action);
    }

    /**
     * 判断是否为扬声器/音频控制设备。
     *
     * @param deviceType 设备类型
     * @return true 表示扬声器/音频设备
     */
    private static boolean isSpeakerDeviceType(String deviceType) {
        return List.of("SPEAKER", "CLIENT_AUDIO", "VOLUME_CONTROL", "INTERCOM").contains(deviceType);
    }

    /**
     * 读取警示灯底层 lightId。
     *
     * @param params 请求参数
     * @param device 设备能力
     * @return lightId
     */
    private static String warningLightId(Map<String, Object> params, Map<String, Object> device) {
        Map<String, Object> profile = mapValue(device.get("controlProfile"));
        String lightId = stringValue(params.get("lightId"), stringValue(profile.get("lightId"), ""));
        if (List.of("light-001", "light-002", "all").contains(lightId)) {
            return lightId;
        }
        throw new IllegalArgumentException("不支持的警示灯 ID：" + lightId);
    }

    /**
     * 根据控制模式推导任务状态。
     *
     * @param controlMode 控制模式
     * @return 任务状态
     */
    private static String missionStatusForMode(String controlMode) {
        return switch (controlMode) {
            case "NAVIGATION" -> "RUNNING";
            case "ASSISTED" -> "ASSISTED";
            default -> "IDLE";
        };
    }

    /**
     * 根据控制模式推导导航状态。
     *
     * @param controlMode 控制模式
     * @return 导航状态
     */
    private static String navigationStatusForMode(String controlMode) {
        return "NAVIGATION".equals(controlMode) ? "RUNNING" : "IDLE";
    }

    /**
     * 读取数值并应用默认值。
     *
     * @param value 待处理值
     * @param defaultValue 默认值
     * @return 数值
     */
    private static Number numberValue(Object value, Number defaultValue) {
        return value instanceof Number number ? number : defaultValue;
    }

    /**
     * 读取 double 值并应用默认值。
     *
     * @param value 待处理值
     * @param defaultValue 默认值
     * @return double 值
     */
    private static double doubleValue(Object value, double defaultValue) {
        return value instanceof Number number ? number.doubleValue() : defaultValue;
    }

    /**
     * 读取 boolean 值并应用默认值。
     *
     * @param value 待处理值
     * @param defaultValue 默认值
     * @return boolean 值
     */
    private static boolean booleanValue(Object value, boolean defaultValue) {
        return value instanceof Boolean bool ? bool : defaultValue;
    }

    /**
     * 读取 OffsetDateTime 值。
     *
     * @param value 待处理值
     * @return 时间值
     */
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

    /**
     * 构造车灯部件状态。
     *
     * @param part 部件状态
     * @return 车灯部件状态
     */
    private static Map<String, Object> vehicleLightPart(Map<String, Object> part) {
        String mode = normalizeVehicleLightMode(stringValue(part.get("mode"), "OFF"));
        int brightness = "CUSTOM".equals(mode)
                ? clampedInt(valueOrDefault(part, "brightness", part.get("customValue")), 0, 0, 100)
                : 0;
        return object("mode", mode, "brightness", brightness);
    }

    private static String normalizeVehicleLightMode(String value) {
        return switch (stringValue(value, "OFF").toUpperCase()) {
            case "ON" -> "ON";
            case "BREATH" -> "BREATH";
            case "CUSTOM" -> "CUSTOM";
            default -> "OFF";
        };
    }

    /**
     * 读取并限制整数范围。
     *
     * @param value 待处理值
     * @param defaultValue 默认值
     * @param min 最小值
     * @param max 最大值
     * @return 限制范围后的整数
     */
    private static int clampedInt(Object value, int defaultValue, int min, int max) {
        int number = value instanceof Number item ? item.intValue() : defaultValue;
        return Math.max(min, Math.min(max, number));
    }

    /**
     * 读取 Map 值并应用默认值。
     *
     * @param map map
     * @param key 字段名
     * @param defaultValue 默认值
     * @return 字段值或默认值
     */
    private static Object valueOrDefault(Map<String, Object> map, String key, Object defaultValue) {
        Object value = map.get(key);
        return value == null ? defaultValue : value;
    }

    /**
     * 限制数值范围。
     *
     * @param value 待处理值
     * @param min 最小值
     * @param max 最大值
     * @return 限制范围后的数值
     */
    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 复制 Map。
     *
     * @param source 源对象
     * @return Map 副本
     */
    private static Map<String, Object> copy(Map<String, Object> source) {
        return new LinkedHashMap<>(source);
    }

    /**
     * 按键值对构造 Map。
     *
     * @param values 键值对数组
     * @return Map 对象
     */
    private static Map<String, Object> object(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length - 1; i += 2) {
            map.put(String.valueOf(values[i]), values[i + 1]);
        }
        return map;
    }
}
