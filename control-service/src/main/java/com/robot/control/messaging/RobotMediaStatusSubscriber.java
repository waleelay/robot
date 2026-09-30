package com.robot.control.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.robot.control.config.ControlServiceProperties;
import com.robot.control.call.IntercomCallCancel;
import com.robot.control.call.IntercomCallInvite;
import com.robot.control.call.IntercomCallService;
import com.robot.control.client.ControlMediaServiceClient;
import com.robot.control.robot.service.RobotRegistryService;
import com.robot.control.service.EquipmentControlService;
import com.robot.control.fixedcamera.FixedCameraHealthService;
import com.robot.control.trajectory.TrajectoryCoordinator;
import com.robot.media.common.video.VideoStatusMessage;
import com.robot.media.common.video.IntercomStatusMessage;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 机器人媒体与客户端状态 MQTT 订阅器。
 *
 * @author leelay
 * @date 2026-07-05
 */
@Component
public class RobotMediaStatusSubscriber {

    private static final Logger log = LoggerFactory.getLogger(RobotMediaStatusSubscriber.class);
    private static final String STATUS_TOPIC = "robot/+/media/video/status";
    private static final String FIXED_CAMERA_STATUS_TOPIC = "gateway/fixed-camera/+/video/status";
    private static final String FIXED_CAMERA_GATEWAY_STATUS_TOPIC = "gateway/fixed-camera/+/status";
    private static final String FIXED_CAMERA_HEALTH_STATUS_TOPIC = "gateway/fixed-camera/+/camera/+/status";
    private static final String INTERCOM_STATUS_TOPIC = "robot/+/media/video/intercom/status";
    private static final String MEDIA_CLIENT_STATUS_TOPIC = "robot/+/media/client/status";
    private static final String EDGE_DEVICE_STATUS_TOPIC = "eiop/v1/edge/+/status";
    private static final String EDGE_TASK_PROGRESS_TOPIC = "eiop/v1/edge/+/tasks/progress";
    private static final String TRAJECTORY_SNAPSHOT_TOPIC = "eiop/v1/edge/+/trajectory/snapshot";
    private static final String CALL_INVITE_TOPIC = "robot/+/media/video/intercom/call/invite";
    private static final String CALL_CANCEL_TOPIC = "robot/+/media/video/intercom/call/cancel";
    private static final String[] STATUS_TOPICS = {
        STATUS_TOPIC, FIXED_CAMERA_STATUS_TOPIC, FIXED_CAMERA_GATEWAY_STATUS_TOPIC, FIXED_CAMERA_HEALTH_STATUS_TOPIC,
        INTERCOM_STATUS_TOPIC, MEDIA_CLIENT_STATUS_TOPIC, CALL_INVITE_TOPIC, CALL_CANCEL_TOPIC,
        EDGE_DEVICE_STATUS_TOPIC, EDGE_TASK_PROGRESS_TOPIC, TRAJECTORY_SNAPSHOT_TOPIC
    };
    /**
     * 与 STATUS_TOPICS 一一对应的订阅 QoS，新增主题时必须保持索引一致。
     */
    private static final int[] STATUS_QOS = {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};

    private final ControlServiceProperties properties;
    private final ObjectMapper objectMapper;
    private final ControlMediaServiceClient mediaServiceClient;
    private final RobotMediaCommandService commandService;
    private final EquipmentControlService equipmentControlService;
    private final RobotRegistryService robotRegistryService;
    private final IntercomCallService intercomCallService;
    private final EdgeDeviceStatusHandler edgeDeviceStatusHandler;
    private final FixedCameraHealthService fixedCameraHealthService;
    private final TrajectoryCoordinator trajectoryCoordinator;
    /**
     * 机器人媒体客户端最近在线标志，用于识别离线到在线的恢复触发。
     */
    private final Map<String, Boolean> mediaClientOnlineByRobot = new ConcurrentHashMap<>();
    private MqttClient client;

    /**
     * 创建 RobotMediaStatusSubscriber 实例。
     * @param properties 服务配置
     * @param objectMapper JSON 编解码器
     * @param mediaServiceClient 媒体服务 客户端
     * @param commandService 视频命令服务
     * @param equipmentControlService 装备控制服务
     * @param robotRegistryService 维护机器人运行状态与在线事实的注册服务
     *
     * @param intercomCallService 在媒体对讲启动前协调机器人主动呼叫的邀请、接听与状态流转。
     * @param edgeDeviceStatusHandler 处理平台边缘设备状态上报，并转换为控制服务统一机器人状态。
     * @param fixedCameraHealthService 保存固定摄像头 Gateway 与 RTSP 最近健康状态。
     * @param trajectoryCoordinator 按大屏实际观看目标查询并定向推送设备任务轨迹。
     */
    public RobotMediaStatusSubscriber(
            ControlServiceProperties properties,
            ObjectMapper objectMapper,
            ControlMediaServiceClient mediaServiceClient,
            RobotMediaCommandService commandService,
            EquipmentControlService equipmentControlService,
            RobotRegistryService robotRegistryService,
            IntercomCallService intercomCallService,
            EdgeDeviceStatusHandler edgeDeviceStatusHandler,
            FixedCameraHealthService fixedCameraHealthService,
            TrajectoryCoordinator trajectoryCoordinator) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.mediaServiceClient = mediaServiceClient;
        this.commandService = commandService;
        this.equipmentControlService = equipmentControlService;
        this.robotRegistryService = robotRegistryService;
        this.intercomCallService = intercomCallService;
        this.edgeDeviceStatusHandler = edgeDeviceStatusHandler;
        this.fixedCameraHealthService = fixedCameraHealthService;
        this.trajectoryCoordinator = trajectoryCoordinator;
    }

    /**
     * 应用启动完成后订阅 MQTT 状态主题。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void subscribeOnReady() {
        if (!properties.getMqtt().isEnabled()) {
            log.info("MQTT 已禁用，跳过媒体状态订阅");
            return;
        }
        try {
            subscribeStatusTopics(mqttClient());
        } catch (MqttException ex) {
            throw new IllegalStateException("订阅媒体 MQTT 主题失败", ex);
        }
    }

    /**
     * 创建对讲状态 MQTT 监听器。
     *
     * @return MQTT 消息监听器
     */
    private IMqttMessageListener intercomStatusListener() {
        return (topic, message) -> {
            String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
            try {
                IntercomStatusMessage status = objectMapper.readValue(payload, IntercomStatusMessage.class);
                if (status.getSessionId() == null || status.getSessionId().isBlank()) {
                    log.debug("对讲状态缺少会话标识，已忽略，主题={} 状态={}", topic, status.getStatus());
                    return;
                }
                intercomCallService.handleIntercomStatus(status.getSessionId(), status.getStatus(), status.getMessage());
                mediaServiceClient.updateIntercomStatus(status);
            } catch (Exception ex) {
                log.warn("处理对讲状态失败，主题={} 载荷字节数={}", topic, message.getPayload().length, ex);
            }
        };
    }

    private IMqttMessageListener callInviteListener() {
        return (topic, message) -> {
            String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
            try {
                intercomCallService.invite(
                        objectMapper.readValue(payload, IntercomCallInvite.class), robotIdFromTopic(topic));
            } catch (Exception ex) {
                log.warn("处理对讲呼叫邀请失败，主题={} 载荷字节数={}", topic, message.getPayload().length, ex);
            }
        };
    }

    private IMqttMessageListener callCancelListener() {
        return (topic, message) -> {
            String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
            try {
                intercomCallService.cancel(
                        objectMapper.readValue(payload, IntercomCallCancel.class), robotIdFromTopic(topic));
            } catch (Exception ex) {
                log.warn("处理对讲呼叫取消失败，主题={} 载荷字节数={}", topic, message.getPayload().length, ex);
            }
        };
    }

    private String robotIdFromTopic(String topic) {
        String[] parts = topic.split("/");
        return parts.length > 1 ? parts[1] : "";
    }

    /**
     * 创建视频状态 MQTT 监听器。
     *
     * @return MQTT 消息监听器
     */
    private IMqttMessageListener statusListener() {
        return (topic, message) -> {
            String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
            try {
                VideoStatusMessage status = objectMapper.readValue(payload, VideoStatusMessage.class);
                mediaServiceClient.updateVideoStatus(status);
            } catch (Exception ex) {
                log.warn("处理媒体状态失败，主题={} 载荷字节数={}", topic, message.getPayload().length, ex);
            }
        };
    }

    /**
     * 创建机器人客户端状态 MQTT 监听器。
     *
     * @return MQTT 消息监听器
     */
    private IMqttMessageListener clientStatusListener() {
        return (topic, message) -> {
            String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
            try {
                Map<String, Object> data = objectMapper.readValue(payload, new TypeReference<Map<String, Object>>() {});
                String topicRobotId = robotIdFromTopic(topic);
                Object reportedRobotIdValue = data.get("robotId");
                String reportedRobotId = reportedRobotIdValue == null ? "" : String.valueOf(reportedRobotIdValue).trim();
                if (!reportedRobotId.isBlank() && !topicRobotId.equals(reportedRobotId)) {
                    log.warn("媒体客户端状态机器人标识 与主题不一致，已忽略，主题机器人标识={} 上报机器人标识={}", topicRobotId, reportedRobotId);
                    return;
                }
                data.put("robotId", topicRobotId);
                Map<String, Object> state = equipmentControlService.handleClientState(data);
                if (state.isEmpty()) {
                    mediaClientOnlineByRobot.remove(topicRobotId);
                    return;
                }
                robotRegistryService.update(state);
                String status = String.valueOf(data.get("status"));
                if (mediaClientBecameOnline(topicRobotId, status)) {
                    mediaServiceClient.onlineRestartCommands(topicRobotId, status).forEach(commandService::sendStart);
                }
            } catch (Exception ex) {
                log.warn("处理媒体客户端状态失败，主题={} 载荷字节数={}", topic, message.getPayload().length, ex);
            }
        };
    }

    boolean mediaClientBecameOnline(String robotId, String status) {
        boolean online = "online".equalsIgnoreCase(status);
        Boolean wasOnline = mediaClientOnlineByRobot.put(robotId, online);
        return online && !Boolean.TRUE.equals(wasOnline);
    }

    private IMqttMessageListener edgeDeviceStatusListener() {
        return this::handleEdgeDeviceStatus;
    }

    void handleEdgeDeviceStatus(String topic, MqttMessage message) {
        if (message.isRetained()) {
            log.info("已忽略 保留的边缘设备状态，不刷新在线时间，主题={}", topic);
            return;
        }
        edgeDeviceStatusHandler.handle(
                topic,
                new String(message.getPayload(), StandardCharsets.UTF_8));
    }

    private IMqttMessageListener edgeTaskProgressListener() {
        return this::handleEdgeTaskProgress;
    }

    @SuppressWarnings("unchecked")
    void handleEdgeTaskProgress(String topic, MqttMessage message) {
        if (message.isRetained()) {
            return;
        }
        try {
            String[] parts = topic == null ? new String[0] : topic.split("/");
            if (parts.length != 6 || !"eiop".equals(parts[0]) || !"v1".equals(parts[1])
                    || !"edge".equals(parts[2]) || parts[3].isBlank()
                    || !"tasks".equals(parts[4]) || !"progress".equals(parts[5])) {
                throw new IllegalArgumentException("无效的设备任务进度 topic：" + topic);
            }
            Map<String, Object> report = objectMapper.readValue(message.getPayload(), Map.class);
            String messageType = String.valueOf(report.getOrDefault("messageType", "")).trim();
            if (!messageType.isBlank() && !"TASK_PROGRESS_REPORT".equals(messageType)) {
                return;
            }
            Map<String, Object> payload = report.get("payload") instanceof Map<?, ?> value
                    ? (Map<String, Object>) value : Map.of();
            if (payload.containsKey("taskInstanceId")) {
                trajectoryCoordinator.observeTaskInstance(parts[3], payload.get("taskInstanceId"));
            }
        } catch (Exception exception) {
            log.warn("处理设备任务进度失败，主题={} 载荷字节数={}", topic, message.getPayload().length, exception);
        }
    }

    private IMqttMessageListener trajectorySnapshotListener() {
        return (topic, message) -> {
            if (message.isRetained()) {
                return;
            }
            trajectoryCoordinator.handleSnapshot(topic, new String(message.getPayload(), StandardCharsets.UTF_8));
        };
    }

    /**
     * 创建并连接 MQTT 客户端。
     *
     * @return MQTT 客户端
     * @throws MqttException MqttException 处理失败时抛出
     */
    private synchronized MqttClient mqttClient() throws MqttException {
        if (client != null && client.isConnected()) {
            return client;
        }
        String clientId = properties.getMqtt().getClientId() + "-subscriber";
        client = new MqttClient(properties.getMqtt().getBrokerUrl(), clientId);
        client.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                try {
                    subscribeStatusTopics(client);
                    log.info("MQTT 状态订阅器已连接，是否重连={} 服务器={}", reconnect, serverURI);
                } catch (MqttException ex) {
                    log.warn("MQTT 连接后重新订阅媒体主题失败，服务器={}", serverURI, ex);
                }
            }

            @Override
            public void connectionLost(Throwable cause) {
                log.warn("MQTT 状态订阅器连接已断开", cause);
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                // 订阅消息由各主题独立注册的监听器处理，此处不重复分派。
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                // 当前客户端仅负责订阅，不需要处理发布完成通知。
            }
        });
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        if (properties.getMqtt().getUsername() != null && !properties.getMqtt().getUsername().isBlank()) {
            options.setUserName(properties.getMqtt().getUsername());
            options.setPassword(properties.getMqtt().getPassword().toCharArray());
        }
        client.connect(options);
        return client;
    }

    /**
     * 订阅机器人媒体状态主题。
     *
     * @param mqttClient MQTT 客户端
     * @throws MqttException 订阅失败时抛出
     */
    private void subscribeStatusTopics(MqttClient mqttClient) throws MqttException {
        mqttClient.subscribe(
                STATUS_TOPICS,
                STATUS_QOS,
                new IMqttMessageListener[] {
                    statusListener(), statusListener(), gatewayStatusListener(), cameraHealthStatusListener(),
                    intercomStatusListener(), clientStatusListener(), callInviteListener(), callCancelListener(),
                    edgeDeviceStatusListener(), edgeTaskProgressListener(), trajectorySnapshotListener()
                });
        log.info("已订阅媒体 MQTT 主题：{} 、{} 、{} 、{} 、{} 、{} 、{} 、{} 、{} 、{} 、{}",
                STATUS_TOPIC, FIXED_CAMERA_STATUS_TOPIC, FIXED_CAMERA_GATEWAY_STATUS_TOPIC, FIXED_CAMERA_HEALTH_STATUS_TOPIC,
                INTERCOM_STATUS_TOPIC, MEDIA_CLIENT_STATUS_TOPIC, CALL_INVITE_TOPIC, CALL_CANCEL_TOPIC,
                EDGE_DEVICE_STATUS_TOPIC, EDGE_TASK_PROGRESS_TOPIC, TRAJECTORY_SNAPSHOT_TOPIC);
    }

    private IMqttMessageListener gatewayStatusListener() {
        return (topic, message) -> fixedCameraHealthService.handleGatewayStatus(topic, message.getPayload());
    }

    private IMqttMessageListener cameraHealthStatusListener() {
        return (topic, message) -> fixedCameraHealthService.handleCameraStatus(topic, message.getPayload());
    }
}
