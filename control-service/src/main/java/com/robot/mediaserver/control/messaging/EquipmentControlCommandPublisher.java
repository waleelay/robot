package com.robot.mediaserver.control.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.mediaserver.control.config.ControlServiceProperties;
import java.util.Map;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class EquipmentControlCommandPublisher {

    private static final Logger log = LoggerFactory.getLogger(EquipmentControlCommandPublisher.class);

    private final ControlServiceProperties properties;
    private final ObjectMapper objectMapper;
    private MqttClient client;

    public EquipmentControlCommandPublisher(ControlServiceProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public void publishCommand(String robotId, Object payload) {
        publish(commandTopic(robotId, payload), payload);
    }

    public void publishEstop(String robotId, Object payload) {
        publish("robot/" + robotId + "/control/safety/estop", payload);
    }

    private String commandTopic(String robotId, Object payload) {
        Map<?, ?> command = objectMapper.convertValue(payload, Map.class);
        Map<?, ?> target = command.get("target") instanceof Map<?, ?> value ? value : Map.of();
        String deviceType = String.valueOf(target.get("deviceType"));
        String action = String.valueOf(command.get("action"));
        String domain = switch (deviceType) {
            case "WHEELED_BASE", "QUADRUPED_BASE", "BIPED_BASE" -> "body";
            case "DUAL_LIGHT_PTZ" -> "ptz";
            case "CLIENT_AUDIO", "INTERCOM", "VOLUME_CONTROL" -> "audio";
            case "LAUNCHER" -> "launcher";
            case "NET_GUN", "NET_LAUNCHER" -> "net-gun";
            case "WARNING_LIGHT" -> "warning-light";
            case "VEHICLE_LIGHT", "SEARCHLIGHT" -> "vehicle-light";
            default -> fallbackDomain(action);
        };
        return "robot/" + robotId + "/control/" + domain + "/command";
    }

    private String fallbackDomain(String action) {
        if (action.startsWith("volume.")) {
            return "audio";
        }
        if (action.startsWith("light.warning.")) {
            return "warning-light";
        }
        if (action.startsWith("light.vehicle.")) {
            return "vehicle-light";
        }
        if (action.startsWith("payload.fire")) {
            return "launcher";
        }
        return "payload";
    }

    private void publish(String topic, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            if (!properties.getMqtt().isEnabled()) {
                log.info("MQTT disabled, skip equipment control publish topic={}, payload={}", topic, json);
                return;
            }
            MqttMessage message = new MqttMessage(json.getBytes());
            message.setQos(1);
            mqttClient().publish(topic, message);
            log.info("Equipment control MQTT published topic={}, payload={}", topic, json);
        } catch (JsonProcessingException | MqttException ex) {
            throw new IllegalStateException("发布设备控制 MQTT 指令失败：" + topic, ex);
        }
    }

    private synchronized MqttClient mqttClient() throws MqttException {
        if (client != null && client.isConnected()) {
            return client;
        }
        client = new MqttClient(properties.getMqtt().getBrokerUrl(), properties.getMqtt().getClientId() + "-equipment-publisher");
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
