package com.robot.mediaserver.control.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.mediaserver.config.MediaProperties;
import com.robot.mediaserver.control.client.ControlMediaServiceClient;
import com.robot.mediaserver.video.messaging.VideoStatusMessage;
import com.robot.mediaserver.video.messaging.IntercomStatusMessage;
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

@Component
public class RobotMediaStatusSubscriber {

    private static final Logger log = LoggerFactory.getLogger(RobotMediaStatusSubscriber.class);
    private static final String STATUS_TOPIC = "robot/+/media/video/status";
    private static final String INTERCOM_STATUS_TOPIC = "robot/+/media/video/intercom/status";
    private static final String CLIENT_STATUS_TOPIC = "robot/+/media/client/status";

    private final MediaProperties properties;
    private final ObjectMapper objectMapper;
    private final ControlMediaServiceClient mediaServiceClient;
    private final RobotMediaCommandService commandService;
    private MqttClient client;

    public RobotMediaStatusSubscriber(
            MediaProperties properties,
            ObjectMapper objectMapper,
            ControlMediaServiceClient mediaServiceClient,
            RobotMediaCommandService commandService) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.mediaServiceClient = mediaServiceClient;
        this.commandService = commandService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void subscribeOnReady() {
        if (!properties.getMqtt().isEnabled()) {
            log.info("MQTT disabled, skip media status subscription");
            return;
        }
        try {
            MqttClient mqttClient = mqttClient();
            mqttClient.subscribe(STATUS_TOPIC, 1, statusListener());
            mqttClient.subscribe(INTERCOM_STATUS_TOPIC, 1, intercomStatusListener());
            mqttClient.subscribe(CLIENT_STATUS_TOPIC, 1, clientStatusListener());
            log.info("Subscribed media MQTT topics: {}, {}, {}", STATUS_TOPIC, INTERCOM_STATUS_TOPIC, CLIENT_STATUS_TOPIC);
        } catch (MqttException ex) {
            throw new IllegalStateException("Failed to subscribe media MQTT topics", ex);
        }
    }

    private IMqttMessageListener intercomStatusListener() {
        return (topic, message) -> {
            String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
            try {
                IntercomStatusMessage status = objectMapper.readValue(payload, IntercomStatusMessage.class);
                mediaServiceClient.updateIntercomStatus(status);
            } catch (Exception ex) {
                log.warn("Failed to handle intercom status topic={}, payload={}", topic, payload, ex);
            }
        };
    }

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

    private IMqttMessageListener clientStatusListener() {
        return (topic, message) -> {
            String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
            try {
                Map<String, Object> data = objectMapper.readValue(payload, Map.class);
                boolean becameOnline = mediaServiceClient.updateRobotClientStatus(data);
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
