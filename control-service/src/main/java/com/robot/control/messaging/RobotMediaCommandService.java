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

/**
 * 机器人媒体 MQTT 指令发布服务。
 *
 * @author leelay
 * @date 2026-07-05
 */
@Service
public class RobotMediaCommandService {

    private static final Logger log = LoggerFactory.getLogger(RobotMediaCommandService.class);

    private final ControlServiceProperties properties;
    private final ObjectMapper objectMapper;
    private MqttClient client;

    /**
     * 创建 RobotMediaCommandService 实例。
     *
     * @param properties 服务配置
     * @param objectMapper JSON 编解码器
     */
    public RobotMediaCommandService(ControlServiceProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 下发视频启动命令。
     *
     * @param command 命令内容
     */
    public void sendStart(VideoStartCommand command) {
        publish("robot/" + command.robotId() + "/media/video/start", command);
    }

    /**
     * 下发视频停止命令。
     *
     * @param robotId 机器人 ID
     * @param payload 消息载荷
     */
    public void sendStop(String robotId, Object payload) {
        publish("robot/" + robotId + "/media/video/stop", payload);
    }

    /**
     * 下发切换通道命令。
     *
     * @param robotId 机器人 ID
     * @param payload 消息载荷
     */
    public void sendSwitchChannel(String robotId, Object payload) {
        publish("robot/" + robotId + "/media/video/switch-channel", payload);
    }

    /**
     * 发送对讲启动命令。
     *
     * @param command 命令内容
     */
    public void sendIntercomStart(IntercomStartCommand command) {
        publish("robot/" + command.robotId() + "/media/video/intercom/start", command);
    }

    /**
     * 下发对讲停止命令。
     *
     * @param robotId 机器人 ID
     * @param payload 消息载荷
     */
    public void sendIntercomStop(String robotId, Object payload) {
        publish("robot/" + robotId + "/media/video/intercom/stop", payload);
    }

    /** Sends the current robot initiated call state back to the robot. */
    public void sendIntercomCallState(String robotId, Object payload) {
        publish("robot/" + robotId + "/media/video/intercom/call/state", payload);
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
