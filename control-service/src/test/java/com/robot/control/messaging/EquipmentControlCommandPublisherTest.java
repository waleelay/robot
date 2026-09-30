package com.robot.control.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.control.config.ControlServiceProperties;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 验证统一设备控制指令的 MQTT 主题和载荷生成。 */
class EquipmentControlCommandPublisherTest {

    @Test
    void routesMultiFunctionCommandsToDedicatedTopic() {
        EquipmentControlCommandPublisher publisher =
                new EquipmentControlCommandPublisher(new ControlServiceProperties(), new ObjectMapper());

        String topic = publisher.commandTopic("robot-001", Map.of(
                "target", Map.of("deviceType", "MULTI_FUNCTION_BROADCASTER"),
                "action", "play_tts"));

        assertThat(topic).isEqualTo("robot/robot-001/control/multi-function/command");
    }
}
