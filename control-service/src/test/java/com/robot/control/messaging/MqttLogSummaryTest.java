package com.robot.control.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.media.common.video.IntercomStartCommand;
import com.robot.media.common.video.VideoChannel;
import com.robot.media.common.video.VideoQuality;
import com.robot.media.common.video.VideoSourceType;
import com.robot.media.common.video.VideoStartCommand;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 验证 MQTT 日志摘要保留诊断信息并避免输出完整敏感载荷。 */
class MqttLogSummaryTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void videoSummaryExcludesTokenUrlsAndRoom() {
        VideoStartCommand command = new VideoStartCommand(
                "cmd-1", "session-1", "camera-1", VideoSourceType.FIXED_CAMERA,
                "camera-1", "camera-1", VideoChannel.visible, VideoQuality.sub,
                "wss://livekit.example", "room-secret", "publisher-secret", "publisher-1",
                "rtsp://user:password@camera.example/live", OffsetDateTime.now());

        String summary = MqttLogSummary.from(objectMapper, command).toString();

        assertThat(summary)
                .contains("命令标识=cmd-1", "会话标识=session-1", "来源类型=FIXED_CAMERA")
                .doesNotContain("publisher-secret", "rtsp://", "password", "livekit.example", "room-secret");
    }

    @Test
    void intercomSummaryExcludesRobotTokenAndLiveKitDetails() {
        IntercomStartCommand command = new IntercomStartCommand(
                "cmd-2", "session-2", "robot-1", "camera-1", "room-secret",
                "wss://livekit.example", "robot-token-secret", true, true, false, OffsetDateTime.now());

        String summary = MqttLogSummary.from(objectMapper, command).toString();

        assertThat(summary)
                .contains("命令标识=cmd-2", "会话标识=session-2", "机器人标识=robot-1")
                .doesNotContain("robot-token-secret", "livekit.example", "room-secret");
    }

    @Test
    void equipmentSummaryExcludesControlParameters() {
        Map<String, Object> command = Map.of(
                "commandId", "cmd-3",
                "action", "fire",
                "target", Map.of("deviceId", "launcher", "deviceType", "LAUNCHER"),
                "params", Map.of("confirmToken", "confirm-secret", "channel", 1));

        String summary = MqttLogSummary.from(objectMapper, command).toString();

        assertThat(summary)
                .contains("命令标识=cmd-3", "动作=fire", "设备类型=LAUNCHER")
                .doesNotContain("confirm-secret", "confirmToken", "params");
    }
}
