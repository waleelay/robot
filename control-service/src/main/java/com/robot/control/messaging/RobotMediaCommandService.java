package com.robot.control.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.control.config.ControlServiceProperties;
import com.robot.control.dto.VideoStartCommand;
import com.robot.control.dto.IntercomStartCommand;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RobotMediaCommandService {

    private static final Logger log = LoggerFactory.getLogger(RobotMediaCommandService.class);

    private final ControlServiceProperties properties;
    private final ObjectMapper objectMapper;
    private MqttClient client;

    public RobotMediaCommandService(ControlServiceProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public void sendStart(VideoStartCommand command) {
        publish("robot/" + command.robotId() + "/media/video/start", command);
    }

    public void sendStop(String robotId, Object payload) {
        publish("robot/" + robotId + "/media/video/stop", payload);
    }

    public void sendSwitchChannel(String robotId, Object payload) {
        publish("robot/" + robotId + "/media/video/switch-channel", payload);
    }

    public void sendIntercomStart(IntercomStartCommand command) {
        publish("robot/" + command.robotId() + "/media/video/intercom/start", command);
    }

    public void sendIntercomStop(String robotId, Object payload) {
        publish("robot/" + robotId + "/media/video/intercom/stop", payload);
    }

    private void publish(String topic, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            if (!properties.getMqtt().isEnabled()) {
                log.info("MQTT disabled, skip publish topic={}, payload={}", topic, json);
                return;
            }
            MqttClient mqtt = mqttClient();
            MqttMessage message = new MqttMessage(json.getBytes());
            message.setQos(1);
            mqtt.publish(topic, message);
            log.info("MQTT published topic={}, payload={}", topic, json);
        } catch (JsonProcessingException | MqttException ex) {
            throw new IllegalStateException("发布 MQTT 指令失败：" + topic, ex);
        }
    }

    private synchronized MqttClient mqttClient() throws MqttException {
        if (client != null && client.isConnected()) {
            return client;
        }
        client = new MqttClient(properties.getMqtt().getBrokerUrl(), properties.getMqtt().getClientId() + "-publisher");
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
