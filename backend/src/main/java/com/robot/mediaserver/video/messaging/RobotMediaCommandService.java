package com.robot.mediaserver.video.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.mediaserver.config.MediaProperties;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 机器人媒体控制指令发送服务。
 *
 * <p>该服务只负责把平台媒体会话指令发布到 EMQX/MQTT。RTSP 地址等设备敏感配置
 * 固定在机器人侧云接入客户端，本服务不会在 MQTT payload 中下发明文 RTSP URL。</p>
 *
 * @author leelay
 * @date 2026/05/19
 */
@Service
public class RobotMediaCommandService {

    private static final Logger log = LoggerFactory.getLogger(RobotMediaCommandService.class);

    private final MediaProperties properties;
    private final ObjectMapper objectMapper;
    private MqttClient client;

    public RobotMediaCommandService(MediaProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 下发开始发布实时视频指令。
     *
     * @param command 开始发布指令
     */
    public void sendStart(VideoStartCommand command) {
        publish("robot/" + command.robotId() + "/media/video/start", command);
    }

    /**
     * 下发停止发布实时视频指令。
     *
     * @param robotId 机器人 ID
     * @param payload 指令内容
     */
    public void sendStop(String robotId, Object payload) {
        publish("robot/" + robotId + "/media/video/stop", payload);
    }

    /**
     * 下发切换媒体通道指令。
     *
     * @param robotId 机器人 ID
     * @param payload 指令内容
     */
    public void sendSwitchChannel(String robotId, Object payload) {
        publish("robot/" + robotId + "/media/video/switch-channel", payload);
    }

    private void publish(String topic, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            if (!properties.getMqtt().isEnabled()) {
                // 默认关闭真实 MQTT 连接，便于在没有 EMQX 时先完成后端和前端闭环。
                log.info("MQTT disabled, skip publish topic={}, payload={}", topic, json);
                return;
            }
            MqttClient mqtt = mqttClient();
            MqttMessage message = new MqttMessage(json.getBytes());
            message.setQos(1);
            mqtt.publish(topic, message);
            log.info("MQTT published topic={}", topic);
        } catch (JsonProcessingException | MqttException ex) {
            throw new IllegalStateException("Failed to publish MQTT command: " + topic, ex);
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
