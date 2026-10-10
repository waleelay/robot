package com.robot.control.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.robot.control.config.ControlServiceProperties;
import com.robot.control.fixedcamera.FixedCameraCatalogSnapshot;
import com.robot.media.common.video.FixedCameraPublisherStopCommand;
import com.robot.media.common.video.VideoChannel;
import com.robot.media.common.video.VideoQuality;
import com.robot.media.common.video.VideoSourceType;
import com.robot.media.common.video.VideoStartCommand;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/** 验证真实 Control 序列化与统一 Gateway Topic，并生成 Go 消费的跨语言契约样例。 */
class MediaGatewayCommandContractTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void publishesLeasedCatalogAndAddressFreeStartForGoConsumer() throws Exception {
        ControlServiceProperties properties = new ControlServiceProperties();
        properties.getMqtt().setEnabled(true);
        properties.getMqtt().setMediaGatewayId("contract-edge");
        RobotMediaCommandService service = new RobotMediaCommandService(properties, mapper);
        MqttClient mqtt = mock(MqttClient.class);
        when(mqtt.isConnected()).thenReturn(true);
        ReflectionTestUtils.setField(service, "client", mqtt);
        Instant now = Instant.now();
        service.sendFixedCameraCatalog(new FixedCameraCatalogSnapshot("1.0", "contract-edge", 1L, now,
                List.of(new FixedCameraCatalogSnapshot.CameraRecord("camera-001", true, "RTSP",
                        "rtsp://camera.example/main", null, now.plusSeconds(180L)))));
        service.sendFixedCameraStart(new VideoStartCommand("cmd-contract", "vs-contract", "camera-001",
                VideoSourceType.FIXED_CAMERA, "camera-001", "camera-001", VideoChannel.visible, VideoQuality.main,
                "wss://livekit.example", "media.fixed.camera-001.visible.main", "test-token",
                "fixed-camera:camera-001", "rtsp://must-not-override.example/main", OffsetDateTime.now().plusMinutes(5L)));
        service.sendFixedCameraStop(new FixedCameraPublisherStopCommand("cmd-stop", "vs-contract", "camera-001",
                "media.fixed.camera-001.visible.main"));
        ArgumentCaptor<MqttMessage> catalog = ArgumentCaptor.forClass(MqttMessage.class);
        ArgumentCaptor<MqttMessage> start = ArgumentCaptor.forClass(MqttMessage.class);
        ArgumentCaptor<MqttMessage> stop = ArgumentCaptor.forClass(MqttMessage.class);
        verify(mqtt).publish(org.mockito.ArgumentMatchers.eq("gateway/media/contract-edge/catalog/sync"), catalog.capture());
        verify(mqtt).publish(org.mockito.ArgumentMatchers.eq("gateway/media/contract-edge/video/start"), start.capture());
        verify(mqtt).publish(org.mockito.ArgumentMatchers.eq("gateway/media/contract-edge/video/stop"), stop.capture());
        JsonNode snapshot = mapper.readTree(catalog.getValue().getPayload());
        JsonNode command = mapper.readTree(start.getValue().getPayload());
        assertThat(snapshot.path("sources").get(0).path("sourceType").asText()).isEqualTo("FIXED_CAMERA");
        assertThat(snapshot.path("issuedAt").asText()).endsWith("Z");
        assertThat(command.path("sourceProtocol").asText()).isEqualTo("RTSP");
        assertThat(command.has("rtspUrl")).isFalse();
        assertThat(start.getValue().getQos()).isEqualTo(1);
        assertThat(start.getValue().isRetained()).isFalse();
        ObjectNode fixture = mapper.createObjectNode();
        fixture.set("catalog", snapshot);
        fixture.set("start", command);
        fixture.set("stop", mapper.readTree(stop.getValue().getPayload()));
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/media-gateway-contract.json"), mapper.writeValueAsString(fixture));
    }
}
