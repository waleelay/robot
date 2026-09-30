package com.robot.mediaserver.tts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.robot.mediaserver.config.MediaProperties;
import com.robot.mediaserver.ws.MediaWebSocketPublisher;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

/** 验证语音发布失败仍关闭音频流并释放文件锁，生成等待中断不被吞掉。 */
class TtsAudioServiceTest {
    @TempDir Path directory;

    @Test
    void closesAudioOnFailureAndAllowsCachedRetry() throws Exception {
        MediaProperties properties = new MediaProperties();
        properties.getTts().setOutputRoot(directory.toString());
        MediaWebSocketPublisher publisher = mock(MediaWebSocketPublisher.class);
        TtsAudioService service = new TtsAudioService(properties, publisher);
        RestTemplate client = (RestTemplate) ReflectionTestUtils.getField(service, "ttsHttp");
        MockRestServiceServer server = MockRestServiceServer.bindTo(client).build();
        server.expect(method(HttpMethod.GET)).andRespond(withSuccess(new byte[]{1, 2, 3}, MediaType.APPLICATION_OCTET_STREAM));
        List<AtomicBoolean> closed = new ArrayList<>();
        try (MockedStatic<AudioSystem> audio = mockStatic(AudioSystem.class)) {
            audio.when(() -> AudioSystem.getAudioInputStream(any(File.class))).thenAnswer(call -> {
                AtomicBoolean flag = new AtomicBoolean();
                closed.add(flag);
                return new AudioInputStream(new ByteArrayInputStream(new byte[]{1, 2, 3}) {
                    @Override public void close() {
                        flag.set(true);
                    }
                }, new AudioFormat(16000, 16, 1, true, false), 1);
            });
            doThrow(new IllegalStateException("test send failure")).when(publisher).publish(anyString(), any());
            assertThatThrownBy(() -> service.generateAndPublishToFrontend("robot-1", "提示音"))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("发布 TTS");
            assertThat(closed.get(0)).isTrue();
            reset(publisher);
            service.generateAndPublishToFrontend("robot-1", "提示音");
            assertThat(closed).hasSize(2);
            assertThat(closed.get(1)).isTrue();
            server.verify();
        }
    }

    @Test
    void preservesInterruptionBeforeGeneration() {
        MediaProperties properties = new MediaProperties();
        properties.getTts().setOutputRoot(directory.toString());
        TtsAudioService service = new TtsAudioService(properties, mock(MediaWebSocketPublisher.class));
        try {
            Thread.currentThread().interrupt();
            assertThatThrownBy(() -> service.generateAndPublishToFrontend("robot-1", "提示音"))
                    .hasCauseInstanceOf(InterruptedException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }
}
