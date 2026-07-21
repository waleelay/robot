package com.robot.control.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.control.config.ControlServiceProperties;
import com.robot.control.call.IntercomCallCancel;
import com.robot.control.call.IntercomCallInvite;
import com.robot.control.call.IntercomCallService;
import com.robot.control.client.ControlMediaServiceClient;
import com.robot.control.robot.service.RobotRegistryService;
import com.robot.control.service.EquipmentControlService;
import com.robot.control.dto.VideoStatusMessage;
import com.robot.control.dto.IntercomStatusMessage;
import java.nio.charset.StandardCharsets;
import java.util.Map;
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
    private static final String INTERCOM_STATUS_TOPIC = "robot/+/media/video/intercom/status";
    private static final String CLIENT_STATUS_TOPIC = "robot/+/media/client/status";
    private static final String CALL_INVITE_TOPIC = "robot/+/media/video/intercom/call/invite";
    private static final String CALL_CANCEL_TOPIC = "robot/+/media/video/intercom/call/cancel";
    private static final String[] STATUS_TOPICS = {
        STATUS_TOPIC, INTERCOM_STATUS_TOPIC, CLIENT_STATUS_TOPIC, CALL_INVITE_TOPIC, CALL_CANCEL_TOPIC
    };
    private static final int[] STATUS_QOS = {1, 1, 1, 1, 1};

    private final ControlServiceProperties properties;
    private final ObjectMapper objectMapper;
    private final ControlMediaServiceClient mediaServiceClient;
    private final RobotMediaCommandService commandService;
    private final EquipmentControlService equipmentControlService;
    private final RobotRegistryService robotRegistryService;
    private final IntercomCallService intercomCallService;
    private MqttClient client;

    /**
     * 创建 RobotMediaStatusSubscriber 实例。
     *
     * @param properties 服务配置
     * @param objectMapper JSON 编解码器
     * @param mediaServiceClient Media Service 客户端
     * @param commandService 视频命令服务
     * @param equipmentControlService 装备控制服务
     * @param robotRegistryService robotRegistryService
     */
    public RobotMediaStatusSubscriber(
            ControlServiceProperties properties,
            ObjectMapper objectMapper,
            ControlMediaServiceClient mediaServiceClient,
            RobotMediaCommandService commandService,
            EquipmentControlService equipmentControlService,
            RobotRegistryService robotRegistryService,
            IntercomCallService intercomCallService) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.mediaServiceClient = mediaServiceClient;
        this.commandService = commandService;
        this.equipmentControlService = equipmentControlService;
        this.robotRegistryService = robotRegistryService;
        this.intercomCallService = intercomCallService;
    }

    /**
     * 应用启动完成后订阅 MQTT 状态 topic。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void subscribeOnReady() {
        if (!properties.getMqtt().isEnabled()) {
            log.info("MQTT disabled, skip media status subscription");
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
                    log.debug("Ignore intercom status without sessionId topic={}, payload={}", topic, payload);
                    return;
                }
                intercomCallService.handleIntercomStatus(status.getSessionId(), status.getStatus(), status.getMessage());
                mediaServiceClient.updateIntercomStatus(status);
            } catch (Exception ex) {
                log.warn("Failed to handle intercom status topic={}, payload={}", topic, payload, ex);
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
                log.warn("Failed to handle intercom call invite topic={}, payload={}", topic, payload, ex);
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
                log.warn("Failed to handle intercom call cancel topic={}, payload={}", topic, payload, ex);
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
                log.warn("Failed to handle media status topic={}, payload={}", topic, payload, ex);
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
                Map<String, Object> data = objectMapper.readValue(payload, Map.class);
                boolean becameOnline = robotRegistryService.update(data);
                equipmentControlService.handleClientState(data);
                if (becameOnline) {
                    String robotId = String.valueOf(data.get("robotId"));
                    String status = String.valueOf(data.get("status"));
                    mediaServiceClient.onlineRestartCommands(robotId, status).forEach(commandService::sendStart);
                }
            } catch (Exception ex) {
                log.warn("Failed to handle media client status topic={}, payload={}", topic, payload, ex);
            }
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
                    log.info("MQTT status subscriber connected reconnect={} server={}", reconnect, serverURI);
                } catch (MqttException ex) {
                    log.warn("Failed to resubscribe media MQTT topics after connect server={}", serverURI, ex);
                }
            }

            @Override
            public void connectionLost(Throwable cause) {
                log.warn("MQTT status subscriber connection lost", cause);
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                // Per-topic listeners handle subscribed messages.
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                // This client only subscribes.
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
     * 订阅机器人媒体状态 topic。
     *
     * @param mqttClient MQTT 客户端
     * @throws MqttException 订阅失败时抛出
     */
    private void subscribeStatusTopics(MqttClient mqttClient) throws MqttException {
        mqttClient.subscribe(
                STATUS_TOPICS,
                STATUS_QOS,
                new IMqttMessageListener[] {
                    statusListener(), intercomStatusListener(), clientStatusListener(), callInviteListener(), callCancelListener()
                });
        log.info("Subscribed media MQTT topics: {}, {}, {}, {}, {}",
                STATUS_TOPIC, INTERCOM_STATUS_TOPIC, CLIENT_STATUS_TOPIC, CALL_INVITE_TOPIC, CALL_CANCEL_TOPIC);
    }
}
