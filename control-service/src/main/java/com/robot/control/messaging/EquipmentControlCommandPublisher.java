package com.robot.control.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.control.config.ControlServiceProperties;
import java.util.Map;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 设备控制 MQTT 指令发布器。
 *
 * @author leelay
 * @date 2026-07-05
 */
@Service
public class EquipmentControlCommandPublisher {

    private static final Logger log = LoggerFactory.getLogger(EquipmentControlCommandPublisher.class);

    private final ControlServiceProperties properties;
    private final ObjectMapper objectMapper;
    private MqttClient client;

    /**
     * 创建 EquipmentControlCommandPublisher 实例。
     *
     * @param properties 服务配置
     * @param objectMapper JSON 编解码器
     */
    public EquipmentControlCommandPublisher(ControlServiceProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 发布设备控制命令。
     *
     * @param robotId 机器人 ID
     * @param payload 消息载荷
     */
    public void publishCommand(String robotId, Object payload) {
        publish(commandTopic(robotId, payload), payload);
    }

    /**
     * 发布急停控制命令。
     *
     * @param robotId 机器人 ID
     * @param payload 消息载荷
     */
    public void publishEstop(String robotId, Object payload) {
        publish("robot/" + robotId + "/control/safety/estop", payload);
    }

    /**
     * 根据控制目标推导 MQTT 命令 topic。
     *
     * @param robotId 机器人 ID
     * @param payload 消息载荷
     * @return MQTT 命令 topic
     */
    private String commandTopic(String robotId, Object payload) {
        Map<?, ?> command = objectMapper.convertValue(payload, Map.class);
        Map<?, ?> target = command.get("target") instanceof Map<?, ?> value ? value : Map.of();
        String deviceType = String.valueOf(target.get("deviceType"));
        String action = String.valueOf(command.get("action"));
        String domain = switch (deviceType) {
            case "WHEELED_BASE", "QUADRUPED_BASE", "BIPED_BASE" -> "body";
            case "DUAL_LIGHT_PTZ" -> "ptz";
            case "SPEAKER", "CLIENT_AUDIO", "INTERCOM", "VOLUME_CONTROL" -> "audio";
            case "LAUNCHER" -> "launcher";
            case "NET_GUN", "NET_LAUNCHER" -> "net-gun";
            case "WARNING_LIGHT" -> "warning-light";
            case "VEHICLE_LIGHT", "SEARCHLIGHT" -> "vehicle-light";
            default -> fallbackDomain(action);
        };
        return "robot/" + robotId + "/control/" + domain + "/command";
    }

    /**
     * 根据动作名称推导默认设备域。
     *
     * @param action 动作名称
     * @return 设备域
     */
    private String fallbackDomain(String action) {
        if (action.startsWith("volume.")) {
            return "audio";
        }
        if (action.startsWith("light.vehicle.")) {
            return "vehicle-light";
        }
        return "payload";
    }

    /**
     * 发布 MQTT 消息。
     *
     * @param topic MQTT topic
     * @param payload 消息载荷
     */
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
