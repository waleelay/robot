package com.robot.control.robot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.control.config.ControlServiceProperties;
import com.robot.control.config.DateTimeConfig;
import com.robot.control.robot.dto.RobotCameraResponse;
import com.robot.control.robot.dto.RobotDeviceResponse;
import com.robot.control.ws.MediaWebSocketPublisher;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * 控制服务 本地机器人在线状态注册表。
 *
 * @author leelay
 * @date 2026-07-05
 */
@Service
public class RobotRegistryService {

    private static final String EDGE_DEVICE_STATUS_SOURCE = "EDGE_DEVICE_STATUS";
    private static final String MEDIA_CLIENT_STATUS_SOURCE = "MEDIA_CLIENT_STATUS";

    private static final List<String> DYNAMIC_STATE_FIELDS = List.of(
            "speed",
            "moving",
            "totalMileage",
            "currentMileage",
            "location",
            "runningStatus",
            "healthStatus",
            "charging",
            "chargingStatus",
            "softStopActive",
            "remoteControlEnabled",
            "taskProgressPercent",
            "taskStatus",
            "edgeStatus",
            "edgeMessageId",
            "edgeSchemaVersion",
            "stateSource");

    private final ControlServiceProperties properties;
    private final MediaWebSocketPublisher webSocketPublisher;
    private final ObjectMapper objectMapper;
    /**
     * 按机器人 ID 保存实例内运行状态；复合字段更新在对应 RobotDevice 监视锁内执行。
     */
    private final Map<String, RobotDevice> devices = new ConcurrentHashMap<>();

    /**
     * 创建 RobotRegistryService 实例。
     *
     * @param properties 服务配置
     * @param webSocketPublisher 向已连接客户端投递业务事件的组件
     * @param objectMapper JSON 编解码器
     */
    public RobotRegistryService(
            ControlServiceProperties properties,
            MediaWebSocketPublisher webSocketPublisher,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.webSocketPublisher = webSocketPublisher;
        this.objectMapper = objectMapper;
    }

    /**
     * 根据机器人客户端上报更新注册表。
     *
     * @param data 业务数据
     * @return 是否从离线变为在线
     */
    public boolean update(Map<String, Object> data) {
        String robotId = string(data.get("robotId"), "");
        String clientId = string(data.get("clientId"), "");
        String status = string(data.get("status"), "");
        String name = string(data.get("name"), robotId);
        String type = string(data.get("type"), null);
        String typeCode = string(data.get("typeCode"), null);
        String controlMode = string(data.get("controlMode"), null);
        Long stateSeq = data.get("stateSeq") instanceof Number seqValue ? seqValue.longValue() : null;
        String missionStatus = string(data.get("missionStatus"), "IDLE");
        String navigationStatus = string(data.get("navigationStatus"), "IDLE");
        Object controlOwner = data.get("controlOwner");
        Boolean estopActive = data.get("estopActive") instanceof Boolean estopValue ? estopValue : null;
        Integer battery = data.get("battery") instanceof Number batteryValue ? batteryValue.intValue() : null;
        List<RobotCameraResponse> cameras = objectMapper.convertValue(
                data.getOrDefault("cameras", List.of()),
                objectMapper.getTypeFactory().constructCollectionType(List.class, RobotCameraResponse.class));
        List<Map<String, Object>> mountedDevices = objectMapper.convertValue(
                data.getOrDefault("devices", List.of()),
                objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
        return update(
                robotId,
                clientId,
                status,
                name,
                type,
                typeCode,
                battery,
                controlMode,
                stateSeq,
                missionStatus,
                navigationStatus,
                controlOwner,
                estopActive,
                cameras,
                mountedDevices,
                data);
    }

    /**
     * 根据机器人客户端上报更新注册表。
     *
     * @param robotId 机器人 ID
     * @param clientId 客户端 ID
     * @param status 状态消息
     * @param name 名称
     * @param type 当前业务使用的类型编码
     * @param battery 设备电量百分数，未提供时为空
     * @param controlMode 控制模式
     * @param stateSeq 设备状态序列号，用于拒绝基于陈旧状态的操作
     * @param missionStatus 设备当前任务状态
     * @param navigationStatus 设备当前导航状态
     * @param controlOwner 设备当前控制权持有者信息
     * @param estopActive 急停是否生效，未知时为空
     * @param cameras 机器人可用摄像头列表
     * @param mountedDevices 机器人挂载组件及其能力列表
     * @return 是否从离线变为在线
     */
    public boolean update(
            String robotId,
            String clientId,
            String status,
            String name,
            String type,
            Integer battery,
            String controlMode,
            Long stateSeq,
            String missionStatus,
            String navigationStatus,
            Object controlOwner,
            Boolean estopActive,
            List<RobotCameraResponse> cameras,
            List<Map<String, Object>> mountedDevices) {
        return update(
                robotId,
                clientId,
                status,
                name,
                type,
                null,
                battery,
                controlMode,
                stateSeq,
                missionStatus,
                navigationStatus,
                controlOwner,
                estopActive,
                cameras,
                mountedDevices);
    }

    /**
     * 合并本次允许覆盖的机器人状态，保留本体与媒体心跳的职责边界并仅发布有效变化。
     * @param robotId 机器人 ID
     * @param clientId 客户端 ID
     * @param status 当前业务状态，取值遵循所属模型的状态协议
     * @param name 当前对象的名称
     * @param type 当前业务使用的类型编码
     * @param typeCode 机器人类型编码
     * @param battery 电量
     * @param controlMode 控制模式
     * @param stateSeq 状态序号
     * @param missionStatus 任务状态
     * @param navigationStatus 导航状态
     * @param controlOwner 控制占用者
     * @param estopActive 急停状态
     * @param cameras 机器人媒体客户端上报的摄像头列表
     * @param mountedDevices 机器人挂载组件及其能力列表
     * @return 是否从离线状态转为在线状态
     */
    public boolean update(
            String robotId,
            String clientId,
            String status,
            String name,
            String type,
            String typeCode,
            Integer battery,
            String controlMode,
            Long stateSeq,
            String missionStatus,
            String navigationStatus,
            Object controlOwner,
            Boolean estopActive,
            List<RobotCameraResponse> cameras,
            List<Map<String, Object>> mountedDevices) {
        return update(
                robotId,
                clientId,
                status,
                name,
                type,
                typeCode,
                battery,
                controlMode,
                stateSeq,
                missionStatus,
                navigationStatus,
                controlOwner,
                estopActive,
                cameras,
                mountedDevices,
                Map.of());
    }

    /**
     * 合并本次允许覆盖的机器人状态，保留本体与媒体心跳的职责边界并仅发布有效变化。
     */
    private boolean update(
            String robotId,
            String clientId,
            String status,
            String name,
            String type,
            String typeCode,
            Integer battery,
            String controlMode,
            Long stateSeq,
            String missionStatus,
            String navigationStatus,
            Object controlOwner,
            Boolean estopActive,
            List<RobotCameraResponse> cameras,
            List<Map<String, Object>> mountedDevices,
            Map<String, Object> dynamicState) {
        if (robotId == null || robotId.isBlank()) {
            return false;
        }
        RobotDevice device = devices.computeIfAbsent(robotId, RobotDevice::new);
        Object stateSource = dynamicState.get("stateSource");
        boolean edgeStatusReport = EDGE_DEVICE_STATUS_SOURCE.equals(stateSource);
        boolean becameOnline;
        boolean publishState;
        long publishVersion;
        Map<String, Object> state;
        synchronized (device) {
            // 在取得设备锁后记录处理时间，保证它晚于已经完成的离线扫描版本，避免并发事件时间倒退。
            OffsetDateTime receivedAt = now();
            boolean wasConnected = "online".equals(device.status) || "fault".equals(device.status);
            device.clientId = clientId;
            device.name = blank(name) ? robotId : name;
            String reportedTypeCode = reportedTypeCode(typeCode, type);
            String reportedType = reportedType(type);
            if (!blank(reportedTypeCode)) {
                device.typeCode = reportedTypeCode;
            }
            if (!blank(reportedType)) {
                device.type = reportedType;
            }
            if (edgeStatusReport && battery != null) {
                device.battery = Math.max(0, Math.min(100, battery));
            }
            if (edgeStatusReport) {
                String normalizedStatus = normalizedStatus(status);
                if (device.lastEdgeStatusAt == null || !normalizedStatus.equals(device.status)) {
                    device.statusChangedAt = receivedAt;
                }
                device.status = normalizedStatus;
                device.lastEdgeStatusAt = receivedAt;
            }
            if (edgeStatusReport && dynamicState.containsKey("controlMode")) {
                device.controlMode = normalizedControlMode(controlMode);
            }
            if (stateSeq != null) {
                device.stateSeq = stateSeq;
            }
            device.missionStatus = blank(missionStatus) ? "IDLE" : missionStatus;
            device.navigationStatus = blank(navigationStatus) ? "IDLE" : navigationStatus;
            device.controlOwner = controlOwner;
            device.estopActive = estopActive == null ? false : estopActive;
            device.lastHeartbeatAt = receivedAt;
            if (cameras != null && !cameras.isEmpty()) {
                device.cameras = new ArrayList<>(cameras);
            }
            if (mountedDevices != null && !mountedDevices.isEmpty()) {
                device.mountedDevices = new ArrayList<>(mountedDevices);
            }
            DYNAMIC_STATE_FIELDS.forEach(field -> {
                if (!edgeStatusReport && ("speed".equals(field) || "location".equals(field))) {
                    return;
                }
                if ("location".equals(field)
                        && dynamicState.containsKey(field)
                        && !shouldAcceptLocation(device.dynamicState.get(field), dynamicState.get(field))) {
                    return;
                }
                if (dynamicState.containsKey(field)
                        && (dynamicState.get(field) != null || "charging".equals(field) || "taskStatus".equals(field))) {
                    device.dynamicState.put(field, dynamicState.get(field));
                }
            });
            becameOnline = !wasConnected && ("online".equals(device.status) || "fault".equals(device.status));
            // 媒体客户端只补充摄像头等附属信息，首个边缘状态到达前没有在线状态真值。
            // 此时保留注册表快照为离线，但不把默认值作为实时状态广播给页面。
            publishState = !MEDIA_CLIENT_STATUS_SOURCE.equals(stateSource) || device.lastEdgeStatusAt != null;
            state = toState(device, receivedAt);
            publishVersion = ++device.publishVersion;
        }
        if (publishState) {
            publishIfCurrent(device, publishVersion, state);
        }
        return becameOnline;
    }

    /**
     * 列出当前注册的机器人状态。
     *
     * @return 列表结果
     */
    public List<RobotDeviceResponse> list() {
        return devices.values().stream()
                .sorted(Comparator.comparing(device -> device.robotId))
                .map(this::toResponse)
                .toList();
    }

    /**
     * 移除本地注册表中的机器人，并向前端广播离线状态。
     *
     * @param robotId 机器人 ID
     */
    public void remove(String robotId) {
        if (robotId == null || robotId.isBlank()) {
            return;
        }
        RobotDevice removed = devices.remove(robotId);
        if (removed == null) {
            return;
        }
        Map<String, Object> state;
        long publishVersion;
        synchronized (removed) {
            OffsetDateTime changedAt = now();
            removed.status = "offline";
            removed.statusChangedAt = changedAt;
            removed.dynamicState.put("stateSource", "UNREGISTERED_DEVICE");
            state = toState(removed, changedAt);
            publishVersion = ++removed.publishVersion;
        }
        publishIfCurrent(removed, publishVersion, state);
    }

    /**
     * 返回指定机器人在内存中保存的最新状态。
     *
     * @param robotId 机器人 ID
     * @return 对应机器人状态；尚未注册时为空
     */
    public Optional<RobotDeviceResponse> find(String robotId) {
        RobotDevice device = devices.get(robotId);
        return device == null ? Optional.empty() : Optional.of(toResponse(device));
    }

    /**
     * 判断设备当前是否仍处于可接收实时位置的在线状态。
     *
     * @param robotId 机器人 ID
     * @return 机器人是否满足当前边缘连接有效性条件
     */
    public boolean isConnected(String robotId) {
        RobotDevice device = devices.get(robotId);
        if (device == null) {
            return false;
        }
        synchronized (device) {
            return "online".equals(device.status) || "fault".equals(device.status);
        }
    }

    /**
     * 原子校验在线状态和当前位置后补充 GIS 坐标，并广播位置状态。
     *
     * @param robotId 机器人 ID
     * @param mapId 所属地图 ID
     * @param x 地图局部坐标 X，单位遵循对应地图协议
     * @param y 地图局部坐标 Y，单位遵循对应地图协议
     * @param longitude GIS 经度，未转换成功时可为空
     * @param latitude GIS 纬度，未转换成功时可为空
     * @return 位置是否仍匹配当前连接并被接受
     */
    public boolean enrichLocationIfConnected(
            String robotId,
            String mapId,
            Double x,
            Double y,
            Double longitude,
            Double latitude) {
        RobotDevice device = devices.get(robotId);
        if (device == null) {
            return false;
        }
        Map<String, Object> state;
        long publishVersion;
        synchronized (device) {
            if (!("online".equals(device.status) || "fault".equals(device.status))
                    || !(device.dynamicState.get("location") instanceof Map<?, ?> current)
                    || !mapId.equals(string(current.get("mapId"), ""))
                    || !sameNumber(x, current.get("x"))
                    || !sameNumber(y, current.get("y"))
                    || current.containsKey("longitude")
                    || current.containsKey("latitude")) {
                return false;
            }
            Map<String, Object> location = new LinkedHashMap<>();
            current.forEach((key, value) -> location.put(String.valueOf(key), value));
            location.put("longitude", longitude);
            location.put("latitude", latitude);
            device.dynamicState.put("location", location);
            device.dynamicState.put("stateSource", "GIS_LOCATION_ENRICHMENT");
            state = toState(device, now());
            publishVersion = ++device.publishVersion;
        }
        publishIfCurrent(device, publishVersion, state);
        return true;
    }

    private void publishIfCurrent(RobotDevice device, long version, Map<String, Object> state) {
        // 状态更新不持有设备锁做网络 I/O，但同设备的旧快照不得晚于新快照下发。
        synchronized (device.publishLock) {
            if (version <= device.lastPublishedVersion) {
                return;
            }
            device.lastPublishedVersion = version;
            webSocketPublisher.publish("robot.state", state);
        }
    }

    /**
     * 扫描并标记心跳超时的机器人。
     */
    public void sweepOffline() {
        OffsetDateTime sweepAt = now();
        OffsetDateTime threshold = sweepAt.minusSeconds(properties.getRobot().getHeartbeatTimeoutSeconds());
        List<StatePublication> offlineEvents = new ArrayList<>();
        devices.values().forEach(device -> {
            synchronized (device) {
                boolean connected = "online".equals(device.status) || "fault".equals(device.status);
                if (connected
                        && device.lastEdgeStatusAt != null
                        && device.lastEdgeStatusAt.isBefore(threshold)) {
                    device.status = "offline";
                    device.statusChangedAt = sweepAt;
                    device.dynamicState.put("stateSource", "OFFLINE_SCAN");
                    offlineEvents.add(new StatePublication(
                            device, ++device.publishVersion, toState(device, sweepAt)));
                }
            }
        });
        offlineEvents.forEach(event -> publishIfCurrent(event.device(), event.version(), event.state()));
        cleanupStaleOffline();
    }

    /**
     * 清理长期离线（超过 {@code control.robot.offline-retention-seconds}）的注册表条目，
     * 避免未注册或已下架设备的内存条目无限累积。
     */
    private void cleanupStaleOffline() {
        OffsetDateTime retentionThreshold = now().minusSeconds(properties.getRobot().getOfflineRetentionSeconds());
        devices.entrySet().removeIf(entry -> {
            RobotDevice device = entry.getValue();
            return "offline".equals(device.status)
                    && device.lastHeartbeatAt != null
                    && device.lastHeartbeatAt.isBefore(retentionThreshold);
        });
    }

    /**
     * 转换为 WebSocket 推送状态。
     *
     * @param device 本次处理的设备档案或运行状态
     * @return WebSocket 状态载荷
     */
    private Map<String, Object> toState(RobotDevice device, OffsetDateTime eventAt) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("robotId", device.robotId);
        state.put("clientId", device.clientId == null ? "" : device.clientId);
        state.put("name", device.name);
        state.put("type", device.type);
        state.put("typeCode", device.typeCode);
        state.put("battery", device.battery);
        state.put("speed", device.dynamicState.get("speed"));
        state.put("runtimeUpdatedAt", runtimeUpdatedAt(device));
        state.put("status", device.status);
        state.put("statusChangedAt", device.statusChangedAt.toString());
        state.put("controlMode", device.controlMode);
        state.put("controlModeName", controlModeName(device.controlMode));
        state.put("stateSeq", device.stateSeq);
        state.put("missionStatus", device.missionStatus);
        state.put("navigationStatus", device.navigationStatus);
        state.put("controlOwner", device.controlOwner);
        state.put("estopActive", device.estopActive);
        state.put("cameras", device.cameras);
        state.put("devices", device.mountedDevices);
        state.putAll(device.dynamicState);
        state.put("timestamp", DateTimeConfig.format(eventAt));
        return state;
    }

    private String normalizedStatus(String status) {
        if ("offline".equalsIgnoreCase(status)) {
            return "offline";
        }
        if ("fault".equalsIgnoreCase(status) || "error".equalsIgnoreCase(status)) {
            return "fault";
        }
        return "online";
    }

    /**
     * 转换为机器人状态响应。
     *
     * @param device 本次处理的设备档案或运行状态
     * @return 机器人状态响应
     */
    private RobotDeviceResponse toResponse(RobotDevice device) {
        synchronized (device) {
            return new RobotDeviceResponse(
                    device.robotId,
                    device.clientId,
                    device.name,
                    device.type,
                    device.typeCode,
                    device.battery,
                    device.status,
                    device.statusChangedAt.toString(),
                    device.controlMode,
                    controlModeName(device.controlMode),
                    device.stateSeq,
                    device.missionStatus,
                    device.navigationStatus,
                    device.controlOwner,
                    device.estopActive,
                    device.lastHeartbeatAt,
                    List.copyOf(device.cameras),
                    List.copyOf(device.mountedDevices),
                    string(device.dynamicState.get("healthStatus"), null),
                    DateTimeConfig.format(device.lastHeartbeatAt),
                    device.dynamicState.get("speed") instanceof Number speed ? speed.doubleValue() : null,
                    runtimeUpdatedAt(device),
                    locationSnapshot(device.dynamicState.get("location")));
        }
    }

    private Map<String, Object> locationSnapshot(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, fieldValue) -> result.put(String.valueOf(key), fieldValue));
        return java.util.Collections.unmodifiableMap(result);
    }

    private boolean shouldAcceptLocation(Object currentValue, Object incomingValue) {
        if (!(currentValue instanceof Map<?, ?> current)) {
            return true;
        }
        if (!(incomingValue instanceof Map<?, ?> incoming)) {
            return false;
        }
        OffsetDateTime currentTime = locationTime(current.get("updatedAt"));
        OffsetDateTime incomingTime = locationTime(incoming.get("updatedAt"));
        if (currentTime != null && incomingTime == null) {
            return false;
        }
        return currentTime == null || incomingTime == null || !incomingTime.isBefore(currentTime);
    }

    private OffsetDateTime locationTime(Object value) {
        try {
            return DateTimeConfig.parseOffsetDateTime(string(value, null));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String controlModeName(String controlMode) {
        return normalizedControlMode(controlMode);
    }

    private String reportedTypeCode(String typeCode, String type) {
        if (!blank(typeCode)) {
            return typeCode.trim();
        }
        return isTypeCode(type) ? type.trim() : null;
    }

    private String reportedType(String type) {
        return blank(type) || isTypeCode(type) ? null : type.trim();
    }

    private boolean isTypeCode(String type) {
        if (blank(type)) {
            return false;
        }
        String value = type.trim();
        return value.equals(value.toUpperCase()) && (value.contains("_") || value.matches("[A-Z0-9]+"));
    }

    private String normalizedControlMode(String controlMode) {
        String mode = controlMode == null ? "" : controlMode.trim();
        if ("导航模式".equals(mode)) {
            return mode;
        }
        return "手动模式".equals(mode) || "常规模式".equals(mode) ? "手动模式" : null;
    }

    private String runtimeUpdatedAt(RobotDevice device) {
        return device.lastEdgeStatusAt == null ? null : device.lastEdgeStatusAt.toString();
    }

    /**
     * 读取字符串值并应用默认值。
     *
     * @param value 待处理值
     * @param defaultValue 默认值
     * @return 字符串值
     */
    private String string(Object value, String defaultValue) {
        return value == null || String.valueOf(value).isBlank() ? defaultValue : String.valueOf(value);
    }

    private boolean sameNumber(Double expected, Object value) {
        return value instanceof Number number && Double.compare(expected, number.doubleValue()) == 0;
    }

    /**
     * 判断字符串是否为空白。
     *
     * @param value 待处理值
     * @return 是否为空白
     */
    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * 返回当前时间。
     *
     * @return 当前时间
     */
    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    /**
     * 带版本的机器人状态发布快照，供锁外事件发送。
     *
     * @param device 本次状态发布所对应的机器人运行对象
     * @param version 当前快照或请求版本，用于识别更新先后
     * @param state 机器人状态
     */
    private record StatePublication(RobotDevice device, long version, Map<String, Object> state) {
    }

    /**
     * 内存中的单台机器人状态快照。
     *
     * @author leelay
     * @date 2026-07-05
     */
    private static class RobotDevice {
        /**
         * 机器人唯一标识，也是注册表键。
         */
        private final String robotId;
        /**
         * 串行发送该机器人状态事件的锁，与运行状态监视锁分离。
         */
        private final Object publishLock = new Object();
        /**
         * 最近生成的状态快照版本，用于跳过锁外迟到发布。
         */
        private long publishVersion;
        /**
         * 已经发出的最新状态版本，受 publishLock 保护。
         */
        private long lastPublishedVersion;
        /**
         * 最近关联的机器人媒体客户端 ID。
         */
        private String clientId;
        /**
         * 机器人展示名称。
         */
        private String name;
        /**
         * 机器人类型名称。
         */
        private String type;
        /**
         * 管理端机器人类型编码。
         */
        private String typeCode;
        /**
         * 最近接受的设备电量百分数，未上报时为空。
         */
        private Integer battery;
        /**
         * 融合后的在线状态，初始化为 offline。
         */
        private String status = "offline";
        /**
         * 在线状态最近变化的服务端时间。
         */
        private OffsetDateTime statusChangedAt = OffsetDateTime.now(ZoneOffset.UTC);
        /**
         * 设备上报的控制模式，未取得事实时为空。
         */
        private String controlMode;
        /**
         * 最近接受的设备状态序号。
         */
        private Long stateSeq = 1L;
        /**
         * 设备最近任务状态，初始为 IDLE。
         */
        private String missionStatus = "IDLE";
        /**
         * 设备最近导航状态，初始为 IDLE。
         */
        private String navigationStatus = "IDLE";
        /**
         * 设备上报的控制占用者信息，未知时为空。
         */
        private Object controlOwner;
        /**
         * 设备急停标志；沿用当前初始化值 false。
         */
        private Boolean estopActive = false;
        /**
         * 最近接受有效心跳的服务端时间，用于离线扫描。
         */
        private OffsetDateTime lastHeartbeatAt;
        /**
         * 最近接受边缘本体状态的服务端时间，不随媒体心跳续期。
         */
        private OffsetDateTime lastEdgeStatusAt;
        /**
         * 媒体客户端提供的摄像头清单。
         */
        private List<RobotCameraResponse> cameras = List.of();
        /**
         * 挂载设备及其运行能力清单。
         */
        private List<Map<String, Object>> mountedDevices = List.of();
        /**
         * 设备动态状态字段；缺省与显式空值按字段更新规则处理。
         */
        private Map<String, Object> dynamicState = new LinkedHashMap<>();

        /**
         * 创建 RobotDevice 实例。
         *
         * @param robotId 机器人 ID
         */
        private RobotDevice(String robotId) {
            this.robotId = robotId;
            this.name = robotId;
        }
    }
}
