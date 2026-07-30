package com.robot.control.service;

import com.robot.control.client.ControlManagementClient;
import com.robot.control.auth.CurrentUser;
import com.robot.control.config.DateTimeConfig;
import com.robot.control.messaging.EquipmentControlCommandPublisher;
import com.robot.control.ws.MediaWebSocketPublisher;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    private static final List<String> MULTI_FUNCTION_ACTIONS = List.of(
            "set_volume",
            "start_broadcast",
            "stop_broadcast",
            "start_monitor",
            "stop_monitor",
            "set_monitor_suppressed",
            "play_tts",
            "stop_tts",
            "list_audio_files",
            "play_audio_file",
            "stop_audio_file",
            "delete_audio_file",
            "play_alarm",
            "stop_alarm",
            "light.set",
            "set_speaker_tilt",
            "set_light_tilt");

    private final EquipmentControlCommandPublisher commandPublisher;
    private final MediaWebSocketPublisher webSocketPublisher;
    private final ControlManagementClient managementClient;
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
            MediaWebSocketPublisher webSocketPublisher,
            ControlManagementClient managementClient) {
        this.commandPublisher = commandPublisher;
        this.webSocketPublisher = webSocketPublisher;
        this.managementClient = managementClient;
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
        String controlMode = reportedControlMode(state.get("controlMode"));
        return object(
                "robotId", robotId,
                "type", robot.get("type"),
                "vendor", robot.get("vendor"),
                "model", robot.get("model"),
                "onlineStatus", valueOrDefault(state, "status", "offline"),
                "controlMode", controlMode,
                "controlModeName", controlModeName(controlMode),
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
    public synchronized Map<String, Object> acquire(String robotId, Map<String, Object> request, CurrentUser user) {
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
     * 从导航模式发起人工接管。
     *
     * <p>该操作不会抢占其他终端的控制会话，也不会乐观修改机器人状态。它先申请本体控制权，
     * 再发布切换手动模式指令，最终模式以机器人客户端状态上报为准。</p>
     *
     * @param robotId 机器人 ID
     * @param request 请求参数
     * @param user 当前用户
     * @return 本体控制会话和模式切换发布结果
     */
    public Map<String, Object> takeover(String robotId, Map<String, Object> request, CurrentUser user) {
        requireRobot(robotId);
        if (request == null || !(request.get("observedStateSeq") instanceof Number observedSeqValue)) {
            throw new IllegalArgumentException("observedStateSeq 必填且必须为数字");
        }
        Map<String, Object> state = robotStates.get(robotId);
        if (state == null || !"online".equalsIgnoreCase(stringValue(state.get("status"), ""))) {
            throw new IllegalArgumentException("机器人不在线，不能人工接管");
        }
        long latestSeq = numberValue(state.get("stateSeq"), 0).longValue();
        if (observedSeqValue.longValue() != latestSeq) {
            return object(
                    "code", "ROBOT_STATE_CHANGED",
                    "message", "机器人状态已变化，请刷新后重试",
                    "latestStateSeq", latestSeq,
                    "latestControlMode", reportedControlMode(state.get("controlMode")),
                    "latestControlModeName", controlModeName(reportedControlMode(state.get("controlMode"))));
        }
        Map<String, Object> session = acquire(robotId, object(
                "scope", "ROBOT",
                "deviceIds", List.of("base"),
                "actions", List.of("control.mode.set", "drive.velocity")), user);
        if (session.containsKey("code")) {
            return session;
        }
        String currentMode = reportedControlMode(state.get("controlMode"));
        Map<String, Object> response = copy(session);
        response.put("controlMode", currentMode);
        response.put("controlModeName", controlModeName(currentMode));
        response.put("stateSeq", latestSeq);
        if ("MANUAL".equals(currentMode)) {
            response.put("modeChangeStatus", "CONFIRMED");
            return response;
        }
        OffsetDateTime issuedAt = publishControlModeCommand(robotId, "MANUAL", latestSeq);
        response.put("modeChangeStatus", "PUBLISHED");
        response.put("requestedControlMode", "MANUAL");
        response.put("requestedControlModeName", "手动模式");
        response.put("issuedAt", issuedAt.toString());
        return response;
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
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String controlMode = normalizeControlMode(requiredString(request, "controlMode"));
        String controlSessionId = requiredString(request, "controlSessionId");
        if (!(request.get("observedStateSeq") instanceof Number observedSeqValue)) {
            throw new IllegalArgumentException("observedStateSeq 必填且必须为数字");
        }
        Map<String, Object> state = robotStates.get(robotId);
        if (state == null || !"online".equalsIgnoreCase(stringValue(state.get("status"), ""))) {
            throw new IllegalArgumentException("机器人不在线，不能切换控制模式");
        }
        long latestSeq = numberValue(state.get("stateSeq"), 0).longValue();
        long observedSeq = observedSeqValue.longValue();
        if (observedSeq != latestSeq) {
            return object(
                    "code", "ROBOT_STATE_CHANGED",
                    "message", "机器人状态已变化，请刷新后重试",
                    "latestStateSeq", latestSeq,
                    "latestControlMode", reportedControlMode(state.get("controlMode")),
                    "latestControlModeName", controlModeName(reportedControlMode(state.get("controlMode"))));
        }
        requireOwnedActiveSession(robotId, controlSessionId, "base", user);
        String currentMode = reportedControlMode(state.get("controlMode"));
        if (controlMode.equals(currentMode)) {
            return object(
                    "status", "CONFIRMED",
                    "robotId", robotId,
                    "controlMode", currentMode,
                    "controlModeName", controlModeName(currentMode),
                    "stateSeq", latestSeq);
        }
        OffsetDateTime now = publishControlModeCommand(robotId, controlMode, latestSeq);
        return object(
                "status", "PUBLISHED",
                "robotId", robotId,
                "requestedControlMode", controlMode,
                "requestedControlModeName", controlModeName(controlMode),
                "controlMode", currentMode,
                "controlModeName", controlModeName(currentMode),
                "stateSeq", latestSeq,
                "issuedAt", now.toString());
    }

    private OffsetDateTime publishControlModeCommand(String robotId, String controlMode, long latestSeq) {
        OffsetDateTime now = OffsetDateTime.now();
        Map<String, Object> base = requireDevice(robotId, "base");
        commandPublisher.publishCommand(robotId, object(
                "robotId", robotId,
                "seq", latestSeq + 1,
                "target", object(
                        "deviceId", "base",
                        "deviceType", base.get("deviceType")),
                "action", "control.mode.set",
                "params", object("controlMode", controlMode),
                "issuedAt", now.toString()));
        return now;
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
        validateCommandAccess(robotId, request, user);
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
     * 发布多合一设备音频文件中转命令。
     *
     * <p>该命令只能由平台上传接口构造，不能由普通控制参数透传。</p>
     *
     * @param robotId 机器人 ID
     * @param deviceId 多合一设备 ID
     * @param params 已校验的下载元数据
     * @return 命令发布结果
     */
    public Map<String, Object> publishMultiFunctionAudioTransfer(
            String robotId,
            String deviceId,
            Map<String, Object> params) {
        requireRobot(robotId);
        Map<String, Object> device = requireDevice(robotId, deviceId);
        String deviceType = stringValue(device.get("deviceType"), "");
        if (!"MULTI_FUNCTION_BROADCASTER".equals(deviceType)) {
            throw new IllegalArgumentException("目标设备不是已注册的多合一设备");
        }
        Map<String, Object> transferParams = audioTransferParams(params);
        OffsetDateTime now = OffsetDateTime.now();
        String commandId = "cmd_" + compactUuid();
        Map<String, Object> mqttPayload = object(
                "robotId", robotId,
                "commandId", commandId,
                "target", object(
                        "deviceId", deviceId,
                        "deviceType", deviceType),
                "action", "upload_audio_file",
                "params", transferParams,
                "issuedAt", now);
        commandPublisher.publishCommand(robotId, mqttPayload);
        Map<String, Object> response = object(
                "commandId", commandId,
                "status", "PUBLISHED",
                "robotId", robotId,
                "target", mqttPayload.get("target"),
                "action", mqttPayload.get("action"),
                "issuedAt", now);
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
        String controlMode = reportedControlMode(state.get("controlMode"));
        state.put("controlMode", controlMode);
        state.put("controlModeName", controlModeName(controlMode));
        state.put("timestamp", DateTimeConfig.normalize(state.getOrDefault("timestamp", OffsetDateTime.now())));
        enrichRobotState(robotId, state);
        Map<String, Map<String, Object>> runtimeDevices = statusByDeviceId(state);
        managementClient.deviceBySerialNumber(robotId)
                .ifPresent(robot -> state.put("devices", devices(robot, runtimeDevices)));
        robotStates.put(robotId, state);
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
        if ("MULTI_FUNCTION_BROADCASTER".equals(deviceType)) {
            return buildMultiFunctionParams(action, params, device);
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
                    "safety_on", booleanValue(params.get("safety_on"), booleanValue(params.get("enabled"), false)),
                    "wait_status", booleanValue(params.get("wait_status"), true));
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

    private Map<String, Object> buildMultiFunctionParams(
            String action,
            Map<String, Object> params,
            Map<String, Object> device) {
        Map<String, Object> profile = mapValue(device.get("controlProfile"));
        return switch (action) {
            case "set_volume" -> {
                Map<String, Object> status = mapValue(device.get("status"));
                int min = clampedInt(profile.get("minVolumePercent"), 0, 0, 100);
                int profileMax = clampedInt(profile.get("maxVolumePercent"), 100, min, 100);
                int max = clampedInt(status.get("volumeLimitPercent"), profileMax, min, profileMax);
                yield object(
                        "volumePercent",
                        clampedInt(valueOrDefault(params, "volumePercent", params.get("volume")), 50, min, max));
            }
            case "start_broadcast", "stop_broadcast", "start_monitor", "stop_monitor" ->
                    object("mediaSessionId", requiredString(params, "mediaSessionId"));
            case "set_monitor_suppressed" ->
                    object("suppressed", requiredBoolean(params, "suppressed"));
            case "play_tts" -> {
                String text = requiredString(params, "text");
                int maxLength = clampedInt(profile.get("maxTextLength"), 500, 1, 5000);
                if (text.length() > maxLength) {
                    throw new IllegalArgumentException("text 长度不能超过 " + maxLength);
                }
                String voice = requiredString(params, "voice").toUpperCase(Locale.ROOT);
                List<String> voices = stringList(profile.get("voices"));
                if (voices.isEmpty()) {
                    voices = List.of("MALE", "FEMALE");
                }
                if (!voices.contains(voice)) {
                    throw new IllegalArgumentException("不支持的 TTS 音色：" + voice);
                }
                yield object(
                        "text", text,
                        "voice", voice,
                        "loop", booleanValue(params.get("loop"), false));
            }
            case "play_audio_file" -> object(
                    "fileName", safeAudioFileName(params),
                    "loop", booleanValue(params.get("loop"), false));
            case "delete_audio_file" -> object("fileName", safeAudioFileName(params));
            case "light.set" -> multiFunctionLightParams(params, profile);
            case "set_speaker_tilt", "set_light_tilt" ->
                    object("positionPercent", clampedInt(params.get("positionPercent"), 50, 0, 100));
            case "stop_tts", "list_audio_files", "stop_audio_file", "play_alarm", "stop_alarm" -> object();
            default -> throw new IllegalArgumentException("多合一设备不支持该动作：" + action);
        };
    }

    private Map<String, Object> multiFunctionLightParams(
            Map<String, Object> params,
            Map<String, Object> profile) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (params.containsKey("enabled")) {
            result.put("enabled", requiredBoolean(params, "enabled"));
        }
        if (params.containsKey("brightness")) {
            result.put("brightness", clampedInt(params.get("brightness"), 50, 0, 100));
        }
        if (params.containsKey("strobeEnabled")) {
            result.put("strobeEnabled", requiredBoolean(params, "strobeEnabled"));
        }
        if (params.containsKey("redBlueMode")) {
            Map<String, Object> light = mapValue(profile.get("light"));
            int min = clampedInt(light.get("redBlueModeMin"), 0, 0, 255);
            int max = clampedInt(light.get("redBlueModeMax"), 16, min, 255);
            result.put("redBlueMode", clampedInt(params.get("redBlueMode"), 0, min, max));
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("light.set 至少需要一个控制参数");
        }
        return result;
    }

    private Map<String, Object> audioTransferParams(Map<String, Object> params) {
        String transferId = requiredString(params, "transferId");
        String fileId = requiredString(params, "fileId");
        String fileName = safeAudioFileName(params);
        String orgId = requiredString(params, "orgId");
        long fileSize = numberValue(params.get("fileSize"), 0).longValue();
        if (fileSize <= 0 || fileSize > 20L * 1024 * 1024) {
            throw new IllegalArgumentException("fileSize 必须大于 0 且不超过 20MB");
        }
        return object(
                "transferId", transferId,
                "fileId", fileId,
                "fileName", fileName,
                "fileSize", fileSize,
                "orgId", orgId);
    }

    private static String safeAudioFileName(Map<String, Object> params) {
        String fileName = requiredString(params, "fileName");
        if (fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            throw new IllegalArgumentException("fileName 不能包含路径");
        }
        return fileName;
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

    private Map<String, Object> requireOwnedActiveSession(
            String robotId,
            String controlSessionId,
            String deviceId,
            CurrentUser user) {
        pruneExpiredSessions(robotId);
        Map<String, Object> session = requireSession(robotId, controlSessionId);
        if (!"ACTIVE".equals(session.get("status")) || isExpired(session, OffsetDateTime.now())) {
            throw new IllegalArgumentException("控制会话已失效，请重新申请");
        }
        if (!user.userId().equals(session.get("ownerUserId"))
                || !user.clientId().equals(session.get("ownerClientId"))) {
            throw new IllegalArgumentException("控制会话不属于当前用户或终端");
        }
        List<String> deviceIds = stringList(session.get("deviceIds"));
        if (!deviceIds.isEmpty() && !deviceIds.contains(deviceId)) {
            throw new IllegalArgumentException("控制会话不包含设备：" + deviceId);
        }
        session.put("leaseExpireAt", OffsetDateTime.now().plusSeconds(30));
        return session;
    }

    private void validateCommandAccess(String robotId, Map<String, Object> request, CurrentUser user) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        Map<String, Object> target = mapValue(request.get("target"));
        String deviceId = stringValue(target.get("deviceId"), "");
        String action = stringValue(request.get("action"), "");
        if (!"base".equals(deviceId) || !"drive.velocity".equals(action)) {
            return;
        }
        Map<String, Object> state = robotStates.get(robotId);
        if (state == null || !"online".equalsIgnoreCase(stringValue(state.get("status"), ""))) {
            throw new IllegalArgumentException("机器人不在线，不能下发本体移动指令");
        }
        String controlMode = reportedControlMode(state.get("controlMode"));
        if (!"MANUAL".equals(controlMode)) {
            throw new IllegalArgumentException("机器人当前为" + controlModeName(controlMode) + "，请先切换到手动模式");
        }
        requireOwnedActiveSession(robotId, requiredString(request, "controlSessionId"), "base", user);
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
        return managementClient.deviceBySerialNumber(robotId)
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
    private Map<String, Object> defaultRobotState(Map<String, Object> robot) {
        return object(
                "robotId", firstValue(robot, "serialNumber", "robotId"),
                "controlMode", "MANUAL",
                "controlModeName", "手动模式",
                "stateSeq", 1,
                "missionStatus", "IDLE",
                "navigationStatus", "IDLE",
                "controlOwner", null,
                "estopActive", false,
                "devices", devices(String.valueOf(firstValue(robot, "serialNumber", "robotId"))),
                "status", "offline",
                "timestamp", OffsetDateTime.now());
    }

    /**
     * 补齐机器人状态的派生字段。
     *
     * @param robotId 机器人 ID
     * @param state 机器人状态
     */
    private void enrichRobotState(String robotId, Map<String, Object> state) {
        String controlMode = reportedControlMode(state.get("controlMode"));
        state.put("controlMode", controlMode);
        state.put("controlModeName", controlModeName(controlMode));
        if (!stringValue(state.get("type"), "").isBlank()) {
            return;
        }
        managementClient.deviceBySerialNumber(robotId)
                .map(robot -> firstValue(robot, "deviceType", "typeCode", "type"))
                .ifPresent(type -> state.put("type", type));
    }

    /**
     * 构造机器人设备能力列表。
     *
     * @param robotId 机器人 ID
     * @return 设备能力列表
     */
    private List<Map<String, Object>> devices(String robotId) {
        Map<String, Object> robot = requireRobot(robotId);
        return devices(robot, statusByDeviceId(robotStates.getOrDefault(robotId, Map.of())));
    }

    private List<Map<String, Object>> devices(
            Map<String, Object> robot,
            Map<String, Map<String, Object>> statusByDeviceId) {
        List<Map<String, Object>> components = mapList(robot.get("components"));
        if (components.isEmpty()) {
            return List.of();
        }
        return components.stream()
                .flatMap(component -> managementDevices(robot, component, statusByDeviceId).stream())
                .filter(Objects::nonNull)
                .toList();
    }

    private List<Map<String, Object>> managementDevices(
            Map<String, Object> robot,
            Map<String, Object> component,
            Map<String, Map<String, Object>> statusByDeviceId) {
        String componentType = normalized(firstString(component, "componentType", "type"));
        String deviceType = controlDeviceType(robot, component);
        if (deviceType == null) {
            return List.of();
        }
        Map<String, Object> primaryDevice = managementDevice(
                robot,
                component,
                controlDeviceId(component, deviceType),
                deviceType,
                firstString(component, "name", "componentName", "code"),
                statusByDeviceId);
        if ("BODY".equals(componentType)
                && hasManagementAction(component, "DEVICE_CONTROL", "SET_LIGHTS")) {
            return List.of(
                    primaryDevice,
                    managementDevice(
                            robot,
                            component,
                            "vehicle-light",
                            "VEHICLE_LIGHT",
                            "车灯光",
                            statusByDeviceId));
        }
        return List.of(primaryDevice);
    }

    private Map<String, Object> managementDevice(
            Map<String, Object> robot,
            Map<String, Object> component,
            String deviceId,
            String deviceType,
            String displayName,
            Map<String, Map<String, Object>> statusByDeviceId) {
        String componentCode = firstString(component, "code", "deviceId", "id");
        Map<String, Object> runtime = statusByDeviceId.getOrDefault(
                deviceId,
                statusByDeviceId.getOrDefault(
                        componentCode,
                        statusByDeviceId.getOrDefault("type:" + deviceType, Map.of())));
        Map<String, Object> result = copy(runtime);
        result.put("deviceId", deviceId);
        result.put("scope", controlScope(deviceType));
        result.put("deviceType", deviceType);
        result.put("displayName", displayName);
        result.put("actions", controlActions(component, deviceType));
        result.put("controlProfile", controlProfile(component, deviceType));
        Map<String, Object> status = mapValue(runtime.get("status"));
        if (!status.isEmpty()) {
            result.put("status", status);
        } else {
            result.remove("status");
        }
        return result;
    }

    private Map<String, Map<String, Object>> statusByDeviceId(Map<String, Object> state) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        Map<String, Integer> typeCounts = new LinkedHashMap<>();
        for (Map<String, Object> device : mapList(state.get("devices"))) {
            String deviceId = firstString(device, "deviceId");
            if (deviceId != null) {
                result.put(deviceId, device);
            }
            String deviceType = firstString(device, "deviceType");
            if (deviceType != null) {
                String typeKey = "type:" + deviceType;
                int count = typeCounts.merge(typeKey, 1, Integer::sum);
                if (count == 1) {
                    result.put(typeKey, device);
                } else {
                    result.remove(typeKey);
                }
            }
            Map<String, Object> status = mapValue(device.get("status"));
            String driverDeviceId = firstString(status, "driverDeviceId");
            if (driverDeviceId != null) {
                result.put(driverDeviceId, device);
            }
        }
        return result;
    }

    private String controlDeviceId(Map<String, Object> component, String deviceType) {
        if ("WHEELED_BASE".equals(deviceType)
                || "QUADRUPED_BASE".equals(deviceType)
                || "BIPED_BASE".equals(deviceType)) {
            return "base";
        }
        return firstString(component, "code", "deviceId", "id");
    }

    private String controlDeviceType(Map<String, Object> robot, Map<String, Object> component) {
        String explicitDeviceType = normalized(firstString(component, "deviceType"));
        String componentType = normalized(firstString(component, "componentType", "type"));
        String code = firstString(component, "code", "deviceId", "id");
        String normalizedCode = normalized(code).replace('-', '_');
        if ("MULTI_FUNCTION_BROADCASTER".equals(explicitDeviceType)
                || "MULTI_FUNCTION_BROADCASTER".equals(componentType)
                || normalizedCode.contains("MULTI_FUNCTION")
                || normalizedCode.contains("FOUR_IN_ONE")
                || normalizedCode.contains("BROADCASTER")) {
            return "MULTI_FUNCTION_BROADCASTER";
        }
        if ("BODY".equals(componentType)) {
            return baseDeviceType(robot);
        }
        if ("PTZ".equals(componentType)) {
            return "DUAL_LIGHT_PTZ";
        }
        if ("SPEAKER".equals(componentType) && code != null && code.contains("microphone")) {
            return "INTERCOM";
        }
        if ("SPEAKER".equals(componentType)) {
            return "SPEAKER";
        }
        if ("ALGORITHM_BOX".equals(componentType)) {
            return "ALGORITHM_BOX";
        }
        if ("PAYLOAD".equals(componentType) && code != null && code.contains("launcher")) {
            return "LAUNCHER";
        }
        if ("PAYLOAD".equals(componentType) && code != null && code.contains("catcher")) {
            return "NET_GUN";
        }
        if ("PAYLOAD".equals(componentType) && code != null && code.contains("warning_light")) {
            return "WARNING_LIGHT";
        }
        if ("PAYLOAD".equals(componentType)
                && (normalizedCode.contains("VEHICLE_LIGHT")
                || managementActionCodes(component).stream()
                        .map(this::normalized)
                        .anyMatch(action -> "LIGHT_VEHICLE_SET".equals(action)
                                || "LIGHT.VEHICLE.SET".equals(action)))) {
            return "VEHICLE_LIGHT";
        }
        return componentType == null || componentType.isBlank() ? null : componentType;
    }

    private String baseDeviceType(Map<String, Object> robot) {
        String robotType = normalized(firstString(robot, "deviceType", "typeCode", "type"));
        if ("ROBOT_DOG".equals(robotType) || "QUADRUPED_ROBOT".equals(robotType)) {
            return "QUADRUPED_BASE";
        }
        return "WHEELED_BASE";
    }

    private String controlScope(String deviceType) {
        if ("WHEELED_BASE".equals(deviceType) || "QUADRUPED_BASE".equals(deviceType)) {
            return "BODY";
        }
        if ("SPEAKER".equals(deviceType) || "INTERCOM".equals(deviceType)) {
            return "AUDIO";
        }
        return "PAYLOAD";
    }

    private List<String> controlActions(Map<String, Object> component, String deviceType) {
        if ("VEHICLE_LIGHT".equals(deviceType)
                && hasManagementAction(component, "DEVICE_CONTROL", "SET_LIGHTS")) {
            return List.of("light.vehicle.set");
        }
        List<String> mapped = new ArrayList<>(managementActionCodes(component).stream()
                .map(code -> controlAction(code, deviceType))
                .filter(Objects::nonNull)
                .distinct()
                .toList());
        List<String> capabilityCodes = managementCapabilityCodes(component);
        if (("WHEELED_BASE".equals(deviceType) || "QUADRUPED_BASE".equals(deviceType))
                && capabilityCodes.contains("MOTION_CONTROL")
                && !mapped.contains("drive.velocity")) {
            mapped.add(0, "drive.velocity");
        }
        if (!mapped.isEmpty()) {
            return mapped;
        }
        return compatibilityActions(deviceType);
    }

    /**
     * 返回已与机器人端联调的生产兼容动作。
     *
     * <p>管理端尚未登记动作时使用此表，避免改变既有 MQTT 协议。这里不包含设备运行状态，
     * 也不用于伪造在线、灯光、音量或弹筒状态。</p>
     */
    private List<String> compatibilityActions(String deviceType) {
        return switch (deviceType) {
            case "WHEELED_BASE", "QUADRUPED_BASE" -> List.of("drive.velocity", "navigation.return_home", "docking.leave");
            case "DUAL_LIGHT_PTZ" -> List.of(
                    "up", "down", "left", "right",
                    "left_up", "right_up", "left_down", "right_down",
                    "ptz.auto_rotate", "camera.zoom");
            case "SPEAKER", "INTERCOM" -> List.of("set_volume", "set_mute");
            case "LAUNCHER" -> List.of("get_status", "set_safety", "fire");
            case "NET_GUN" -> List.of("fire");
            case "WARNING_LIGHT" -> List.of("get_state", "set_state", "set_mode");
            case "VEHICLE_LIGHT" -> List.of("light.vehicle.set");
            case "MULTI_FUNCTION_BROADCASTER" -> MULTI_FUNCTION_ACTIONS;
            default -> List.of();
        };
    }

    private String controlAction(String managementActionCode, String deviceType) {
        String code = normalized(managementActionCode);
        return switch (code) {
            case "DRIVE.VELOCITY", "DRIVE_VELOCITY", "TELEOP_DRIVE" -> "drive.velocity";
            case "PTZ_UP", "UP" -> "up";
            case "PTZ_DOWN", "DOWN" -> "down";
            case "PTZ_LEFT", "LEFT" -> "left";
            case "PTZ_RIGHT", "RIGHT" -> "right";
            case "PTZ_LEFT_UP", "LEFT_UP" -> "left_up";
            case "PTZ_RIGHT_UP", "RIGHT_UP" -> "right_up";
            case "PTZ_LEFT_DOWN", "LEFT_DOWN" -> "left_down";
            case "PTZ_RIGHT_DOWN", "RIGHT_DOWN" -> "right_down";
            case "PTZ.AUTO_ROTATE", "PTZ_AUTO_ROTATE" -> "ptz.auto_rotate";
            case "PTZ.HOME", "PTZ_HOME" -> "ptz.home";
            case "CAMERA.ZOOM", "CAMERA_ZOOM" -> "camera.zoom";
            case "GET_LAUNCHER_STATUS", "GET_STATUS" -> "get_status";
            case "SET_LAUNCHER_SAFETY", "SET_SAFETY" -> "set_safety";
            case "FIRE_LAUNCHER", "FIRE_CATCHER", "FIRE" -> "fire";
            case "GET_LIGHT_STATE", "GET_STATE" -> "get_state";
            case "SET_LIGHT_STATE", "SET_STATE" -> "set_state";
            case "SET_LIGHT_MODE", "SET_MODE" -> "set_mode";
            case "SET_SPEAKER_VOLUME", "SET_VOLUME" -> "set_volume";
            case "SET_SPEAKER_MUTE", "SET_MUTE" -> "set_mute";
            case "MOVE_TO_POSE" -> "navigation.return_home";
            case "NAVIGATION.RETURN_HOME", "NAVIGATION_RETURN_HOME" -> "navigation.return_home";
            case "DOCKING.LEAVE", "DOCKING_LEAVE" -> "docking.leave";
            case "LIGHT.SET", "LIGHT_SET" -> "light.set";
            case "LIGHT.VEHICLE.SET", "LIGHT_VEHICLE_SET" -> "light.vehicle.set";
            case "SET_LIGHTS" -> "VEHICLE_LIGHT".equals(deviceType) ? "light.vehicle.set" : null;
            case "START_BROADCAST" -> "start_broadcast";
            case "STOP_BROADCAST" -> "stop_broadcast";
            case "START_MONITOR" -> "start_monitor";
            case "STOP_MONITOR" -> "stop_monitor";
            case "SET_MONITOR_SUPPRESSED" -> "set_monitor_suppressed";
            case "PLAY_TTS" -> "play_tts";
            case "STOP_TTS" -> "stop_tts";
            case "LIST_AUDIO_FILES" -> "list_audio_files";
            case "UPLOAD_AUDIO_FILE" -> "upload_audio_file";
            case "PLAY_AUDIO_FILE" -> "play_audio_file";
            case "STOP_AUDIO_FILE" -> "stop_audio_file";
            case "DELETE_AUDIO_FILE" -> "delete_audio_file";
            case "PLAY_ALARM" -> "play_alarm";
            case "STOP_ALARM" -> "stop_alarm";
            case "SET_SPEAKER_TILT" -> "set_speaker_tilt";
            case "SET_LIGHT_TILT" -> "set_light_tilt";
            default -> "ALGORITHM_BOX".equals(deviceType) ? code : null;
        };
    }

    private List<String> managementActionCodes(Map<String, Object> component) {
        return mapList(component.get("capabilities")).stream()
                .flatMap(capability -> mapList(capability.get("actions")).stream())
                .map(action -> firstString(action, "code", "capabilityCode", "name"))
                .filter(Objects::nonNull)
                .map(this::normalized)
                .distinct()
                .toList();
    }

    private boolean hasManagementAction(
            Map<String, Object> component,
            String capabilityCode,
            String actionCode) {
        String expectedCapability = normalized(capabilityCode);
        String expectedAction = normalized(actionCode);
        return mapList(component.get("capabilities")).stream()
                .filter(capability -> expectedCapability.equals(
                        normalized(firstString(capability, "code", "capabilityCode", "name"))))
                .flatMap(capability -> mapList(capability.get("actions")).stream())
                .map(action -> firstString(action, "code", "capabilityCode", "name"))
                .filter(Objects::nonNull)
                .map(this::normalized)
                .anyMatch(expectedAction::equals);
    }

    private List<String> managementCapabilityCodes(Map<String, Object> component) {
        return mapList(component.get("capabilities")).stream()
                .map(capability -> firstString(capability, "code", "capabilityCode", "name"))
                .filter(Objects::nonNull)
                .map(this::normalized)
                .distinct()
                .toList();
    }

    private Map<String, Object> controlProfile(Map<String, Object> component, String deviceType) {
        Map<String, Object> profile = compatibilityControlProfile(component, deviceType);
        Map<String, Object> registeredProfile = mapValue(component.get("controlProfile"));
        registeredProfile.forEach((key, value) -> {
            if (value != null) {
                profile.put(key, value);
            }
        });
        return profile;
    }

    /**
     * 返回已经完成端到端联调的参数范围和安全约束。
     *
     * <p>这些值只参与前端控件范围与服务端参数裁剪，不改变 MQTT action/params 字段名。
     * 管理端登记了 controlProfile 时，以管理端非空字段覆盖这里的兼容值。</p>
     */
    private Map<String, Object> compatibilityControlProfile(
            Map<String, Object> component,
            String deviceType) {
        if ("WHEELED_BASE".equals(deviceType)) {
            return object("maxLinearX", 1.0, "maxLinearY", 0.4, "maxAngularZ", 0.8, "controlFrameRateHz", 10);
        }
        if ("QUADRUPED_BASE".equals(deviceType)) {
            return object("maxLinearX", 0.8, "maxLinearY", 0.4, "maxAngularZ", 0.6, "controlFrameRateHz", 10);
        }
        if ("DUAL_LIGHT_PTZ".equals(deviceType)) {
            return object("maxPanSpeed", 100.0, "maxTiltSpeed", 100.0, "controlFrameRateHz", 10);
        }
        if ("SPEAKER".equals(deviceType) || "INTERCOM".equals(deviceType)) {
            return object("step", 5, "minVolume", 0, "maxVolume", 100);
        }
        if ("LAUNCHER".equals(deviceType)) {
            return object("tubes", List.of(1, 2, 3, 4, 5, 6), "requiresConfirm", true, "requiresSafetySwitch", true);
        }
        if ("NET_GUN".equals(deviceType)) {
            return object("requiresConfirm", true, "cooldownMs", 3000);
        }
        if ("WARNING_LIGHT".equals(deviceType)) {
            return object(
                    "lightId", "all",
                    "lightIds", List.of("light-001", "light-002", "all"),
                    "modes", List.of(0, 1, 2),
                    "supportsAll", true);
        }
        if ("VEHICLE_LIGHT".equals(deviceType)) {
            return object(
                    "modes", List.of("OFF", "ON", "BREATH", "CUSTOM"),
                    "minBrightness", 0,
                    "maxBrightness", 100);
        }
        if ("MULTI_FUNCTION_BROADCASTER".equals(deviceType)) {
            return object(
                    "minVolumePercent", 0,
                    "maxVolumePercent", 100,
                    "maxTextLength", 500,
                    "voices", List.of("MALE", "FEMALE"),
                    "audioFormats", List.of("mp3", "wav"),
                    "monitor", object("supportsSuppression", true),
                    "light", object(
                            "minBrightnessPercent", 0,
                            "maxBrightnessPercent", 100,
                            "supportsStrobe", true,
                            "redBlueModeMin", 0,
                            "redBlueModeMax", 16,
                            "stateReadable", false),
                    "speakerTilt", object("minPositionPercent", 0, "maxPositionPercent", 100),
                    "lightTilt", object("minPositionPercent", 0, "maxPositionPercent", 100));
        }
        return object("componentType", firstString(component, "componentType"), "capabilities", managementActionCodes(component));
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
        String mode = stringValue(value, "").trim().toUpperCase(Locale.ROOT);
        if (List.of("MANUAL", "NAVIGATION").contains(mode)) {
            return mode;
        }
        throw new IllegalArgumentException("不支持的控制模式：" + value);
    }

    private static String reportedControlMode(Object value) {
        String mode = stringValue(value, "MANUAL").trim().toUpperCase(Locale.ROOT);
        return "MANUAL".equals(mode) ? "MANUAL" : "NAVIGATION";
    }

    private static String controlModeName(String controlMode) {
        return "MANUAL".equals(controlMode) ? "手动模式" : "导航模式";
    }

    private static String requiredString(Map<String, Object> source, String field) {
        String value = stringValue(source.get(field), "").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(field + " 必填");
        }
        return value;
    }

    private static boolean requiredBoolean(Map<String, Object> source, String field) {
        Object value = source.get(field);
        if (!(value instanceof Boolean bool)) {
            throw new IllegalArgumentException(field + " 必须为 boolean");
        }
        return bool;
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

    private static Object firstValue(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String firstString(Map<String, Object> source, String... keys) {
        Object value = firstValue(source, keys);
        return value == null ? null : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) new LinkedHashMap<>((Map<String, Object>) item))
                .toList();
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
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
