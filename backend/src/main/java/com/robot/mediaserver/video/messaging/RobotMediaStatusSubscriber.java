package com.robot.mediaserver.video.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.mediaserver.config.MediaProperties;
import com.robot.mediaserver.video.service.VideoSessionService;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 机器人媒体状态 MQTT 订阅器。
 *
 * <p>订阅云接入客户端上报的 ACK 和 status 消息，并推进实时视频会话状态机。</p>
 *
 * @author leelay
 * @date 2026/05/20
 */
@Component
public class RobotMediaStatusSubscriber {

    private static final Logger log = LoggerFactory.getLogger(RobotMediaStatusSubscriber.class);
    private static final String ACK_TOPIC = "robot/+/media/video/ack";
    private static final String STATUS_TOPIC = "robot/+/media/video/status";
    private static final String CLIENT_STATUS_TOPIC = "robot/+/media/client/status";

    private final MediaProperties properties;
    private final ObjectMapper objectMapper;
    private final VideoSessionService videoSessionService;
    private MqttClient client;

    public RobotMediaStatusSubscriber(
            MediaProperties properties,
            ObjectMapper objectMapper,
            VideoSessionService videoSessionService) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.videoSessionService = videoSessionService;
    }

    /**
     * 应用启动后订阅媒体状态 Topic。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void subscribeOnReady() {
        if (!properties.getMqtt().isEnabled()) {
            log.info("MQTT disabled, skip media status subscription");
            return;
        }
        try {
            MqttClient mqttClient = mqttClient();
            mqttClient.subscribe(ACK_TOPIC, 1, ackListener());
            mqttClient.subscribe(STATUS_TOPIC, 1, statusListener());
            mqttClient.subscribe(CLIENT_STATUS_TOPIC, 1, clientStatusListener());
            log.info("Subscribed media MQTT topics: {}, {}", ACK_TOPIC, STATUS_TOPIC);
        } catch (MqttException ex) {
            throw new IllegalStateException("Failed to subscribe media MQTT topics", ex);
        }
    }

    private IMqttMessageListener ackListener() {
        return (topic, message) -> {
            String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
            try {
                VideoAckMessage ack = objectMapper.readValue(payload, VideoAckMessage.class);
                videoSessionService.handleClientAck(ack.getSessionId(), ack.isSuccess(), ack.getMessage());
            } catch (Exception ex) {
                log.warn("Failed to handle media ack topic={}, payload={}", topic, payload, ex);
            }
        };
    }

    private IMqttMessageListener statusListener() {
        return (topic, message) -> {
            String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
            try {
                VideoStatusMessage status = objectMapper.readValue(payload, VideoStatusMessage.class);
                videoSessionService.handleClientStatus(
                        status.getSessionId(),
                        status.getStatus(),
                        status.getTrackSid(),
                        status.getTrackName(),
                        status.getErrorCode(),
                        status.getMessage());
            } catch (Exception ex) {
                log.warn("Failed to handle media status topic={}, payload={}", topic, payload, ex);
            }
        };
    }

    private IMqttMessageListener clientStatusListener() {
        return (topic, message) -> {
            String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
            try {
                Map<String, Object> data = objectMapper.readValue(payload, Map.class);
                String robotId = String.valueOf(data.get("robotId"));
                String status = String.valueOf(data.get("status"));
                videoSessionService.handleClientOnline(robotId, status);
            } catch (Exception ex) {
                log.warn("Failed to handle media client status topic={}, payload={}", topic, payload, ex);
            }
        };
    }

    private synchronized MqttClient mqttClient() throws MqttException {
        if (client != null && client.isConnected()) {
            return client;
        }
        String clientId = properties.getMqtt().getClientId() + "-subscriber";
        client = new MqttClient(properties.getMqtt().getBrokerUrl(), clientId);
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
}
