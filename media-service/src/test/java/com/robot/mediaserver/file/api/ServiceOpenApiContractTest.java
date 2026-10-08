package com.robot.mediaserver.file.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.media.common.video.FixedCameraIngressResponse;
import com.robot.media.common.video.VideoSessionResponse;
import com.robot.mediaserver.auth.CurrentUserResolver;
import com.robot.mediaserver.config.DateTimeConfig;
import com.robot.mediaserver.config.MediaProperties;
import com.robot.mediaserver.fieldcall.FieldCallMediaService;
import com.robot.mediaserver.fieldcall.api.FieldCallController;
import com.robot.mediaserver.livekit.LiveKitWebhookController;
import com.robot.mediaserver.livekit.LiveKitWebhookService;
import com.robot.mediaserver.tts.api.RobotTtsController;
import com.robot.mediaserver.tts.service.TtsAudioService;
import com.robot.mediaserver.video.api.ApiExceptionHandler;
import com.robot.mediaserver.video.api.FixedCameraIngressController;
import com.robot.mediaserver.video.api.FixedCameraSourceController;
import com.robot.mediaserver.video.api.VideoSessionController;
import com.robot.mediaserver.video.service.FixedCameraIngressService;
import com.robot.mediaserver.video.service.MediaTrackService;
import com.robot.mediaserver.video.service.VideoSessionService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** 验证所有非文件映射进入契约，并通过真实 HTTP 检查视频、版本头、鉴权、空正文与二进制边界。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = ServiceOpenApiContractTest.Application.class,
        properties = {"springdoc.api-docs.enabled=true", "spring.main.banner-mode=off"})
class ServiceOpenApiContractTest {
    private static final String VIDEO = "/internal/media/video-sessions";
    @Autowired private TestRestTemplate http;
    @Autowired private ObjectMapper mapper;
    @Autowired private RequestMappingHandlerMapping mappings;
    @MockitoBean private VideoSessionService videos;
    @MockitoBean private MediaTrackService tracks;
    @MockitoBean private FixedCameraIngressService ingress;
    @MockitoBean private FieldCallMediaService calls;
    @MockitoBean private LiveKitWebhookService webhooks;
    @MockitoBean private TtsAudioService tts;
    @MockitoBean private com.robot.mediaserver.file.service.FileService files;
    @MockitoBean private com.robot.mediaserver.file.service.FileMultipartCompletionService completion;
    @MockitoBean private com.robot.mediaserver.file.progress.FileUploadProgressService progress;
    @TempDir private Path temporary;
    private JsonNode contract;
    private FileContractSupport support;

    @BeforeEach
    void setUp() throws Exception {
        contract = mapper.readTree(http.getForObject("/v3/api-docs/service", String.class));
        support = new FileContractSupport(mapper, contract);
    }

    @Test
    void keepsDefaultFileContractStableWithAllControllersLoaded() throws Exception {
        JsonNode actual = mapper.readTree(http.getForObject("/v3/api-docs", String.class));
        assertThat(actual).isEqualTo(mapper.readTree(Path.of("../quality/openapi/media-files.json").toFile()));
    }

    @Test
    void coversEveryMappedOperationAndKeepsStableSnapshot() throws Exception {
        Set<String> actual = new HashSet<>();
        mappings.getHandlerMethods().forEach((mapping, handler) -> {
            if (handler.getBeanType().getPackageName().startsWith("com.robot.mediaserver")
                    && !handler.getBeanType().getPackageName().contains(".file.")) {
                mapping.getPatternValues().forEach(path -> mapping.getMethodsCondition().getMethods()
                        .forEach(method -> actual.add(method.name().toLowerCase() + " " + path)));
            }
        });
        Set<String> documented = new HashSet<>();
        Set<String> ids = new HashSet<>();
        contract.path("paths").properties().forEach(path -> path.getValue().properties().forEach(operation -> {
            documented.add(operation.getKey() + " " + path.getKey());
            assertThat(ids.add(operation.getValue().path("operationId").asText())).isTrue();
        }));
        assertThat(documented).containsExactlyInAnyOrderElementsOf(actual).hasSize(43);
        support.snapshot("media-service.json", contract);
    }

    @Test
    void serializesVideoNullsEnumsAndShanghaiTimes() throws Exception {
        VideoSessionResponse session = mapper.readValue("""
                {"sessionId":"session-1","robotId":"robot-1","deviceId":"camera-1", "sourceType":"ROBOT_CAMERA",
                 "sourceId":"robot-1","publisherMode":"DEVICE_CLIENT","publisherRevision":1,"channel":"visible",
                 "quality":"sub","status":"REQUESTING_CLIENT","roomName":"contract-room","livekitUrl":"wss://media.example",
                 "viewerToken":"fixture-token","viewerCount":1,"intercomStatus":"IDLE","intercomAudioOnly":false,
                 "createdAt":"2026-09-29 12:00:00","updatedAt":"2026-09-29 12:00:00"}
                """, VideoSessionResponse.class);
        when(videos.create(any(), any())).thenReturn(session);
        JsonNode body = json("post", VIDEO, VIDEO, Map.of("robotId", "robot-1", "deviceId", "camera-1", "channel", "visible"), headers(), 200);
        support.validateRequest(VIDEO, "post", mapper.valueToTree(Map.of("robotId", "robot-1", "deviceId", "camera-1", "channel", "visible")));
        assertThat(body.path("createdAt").asText()).isEqualTo("2026-09-29 12:00:00");
        assertThat(body.path("trackSid").isNull()).isTrue();
        support.snapshot("fixtures/video-session.json", body);
        json("post", VIDEO, VIDEO, Map.of(), headers(), 400);
        when(videos.create(any(), any())).thenThrow(new IllegalStateException("会话状态冲突"));
        json("post", VIDEO, VIDEO, Map.of("robotId", "r", "deviceId", "d", "channel", "visible"), headers(), 409);
    }

    @Test
    void requiresIngressRevisionAndPreservesNullableUnconfiguredResponse() throws Exception {
        String path = "/internal/media/fixed-camera-ingresses";
        FixedCameraIngressResponse response = mapper.readValue("{\"cameraId\":\"camera-1\",\"status\":\"UNCONFIGURED\"}", FixedCameraIngressResponse.class);
        when(ingress.create(anyString(), anyLong())).thenReturn(response);
        HttpHeaders headers = headers();
        headers.set("X-Ingress-Operation-Revision", "7");
        json("post", path, path, Map.of("cameraId", "camera-1"), headers, 200);
        verify(ingress).create("camera-1", 7L);
        json("post", path, path, Map.of("cameraId", "camera-1"), headers(), 400);
        headers.set("X-Ingress-Operation-Revision", "bad");
        json("post", path, path, Map.of("cameraId", "camera-1"), headers, 404);
    }

    @Test
    void rejectsUntrustedPublisherCaller() throws Exception {
        String path = "/internal/media/fixed-camera-sources/{cameraId}/publisher-presence";
        json("get", path, path.replace("{cameraId}", "camera-1"), null, headers(), 403);
    }

    @Test
    void preservesEmptyRecordingAndStatusResponses() {
        ResponseEntity<byte[]> absent = request("get", VIDEO + "/session-1/recordings/active", null, headers());
        assertThat(absent.getStatusCode().value()).isEqualTo(200);
        assertThat(absent.getBody()).isNullOrEmpty();
        ResponseEntity<byte[]> status = request("post", VIDEO + "/status", Map.of("sessionId", "session-1", "status", "STREAMING"), headers());
        assertThat(status.getStatusCode().value()).isEqualTo(200);
        assertThat(status.getBody()).isNullOrEmpty();
        verify(videos).handleClientStatus("session-1", "STREAMING", null, null, null, null);
    }

    @Test
    void preservesWebhookNoContentAndAuthenticationFailure() {
        HttpHeaders headers = headers();
        headers.setContentType(MediaType.parseMediaType("application/webhook+json"));
        ResponseEntity<byte[]> ok = request("post", "/internal/media/livekit/webhook", "{}", headers);
        assertThat(ok.getStatusCode().value()).isEqualTo(204);
        doThrow(new LiveKitWebhookService.InvalidWebhookAuthenticationException("bad signature"))
                .when(webhooks).receive(any(), any());
        ResponseEntity<byte[]> denied = request("post", "/internal/media/livekit/webhook", "{}", headers);
        assertThat(denied.getStatusCode().value()).isEqualTo(401);
        assertThat(denied.getBody()).isNullOrEmpty();
    }

    @Test
    void returnsTtsFileBytesAndRequiresRobotIdentity() throws Exception {
        Path wav = temporary.resolve("speech.wav");
        Files.write(wav, new byte[]{82, 73, 70, 70});
        when(tts.generateAndReturnFile("robot-1", "hello")).thenReturn(ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/wav")).header("X-TTS-Cache-Hit", "true")
                .body(new FileSystemResource(wav)));
        HttpHeaders headers = headers();
        headers.set("X-Robot-Id", "robot-1");
        ResponseEntity<byte[]> file = request("get", "/api/media/tts/generate-file?text=hello", null, headers);
        assertThat(file.getBody()).containsExactly(82, 73, 70, 70);
        assertThat(file.getHeaders().getFirst("X-TTS-Cache-Hit")).isEqualTo("true");
        assertThat(file.getHeaders().getContentType().toString()).isEqualTo("audio/wav");
        json("get", "/api/media/tts/generate-file", "/api/media/tts/generate-file?text=hello", null, headers(), 400);
    }

    private JsonNode json(String method, String template, String url, Object body, HttpHeaders headers, int status) throws Exception {
        ResponseEntity<byte[]> response = request(method, url, body, headers);
        assertThat(response.getStatusCode().value()).as("%s %s", method, url).isEqualTo(status);
        JsonNode value = mapper.readTree(response.getBody());
        support.validate(template, method, status, value);
        return value;
    }

    private ResponseEntity<byte[]> request(String method, String url, Object body, HttpHeaders headers) {
        return http.exchange(url, HttpMethod.valueOf(method.toUpperCase()), new HttpEntity<>(body, headers), byte[].class);
    }

    private HttpHeaders headers() {
        HttpHeaders result = new HttpHeaders();
        result.setContentType(MediaType.APPLICATION_JSON);
        result.set("X-User-Id", "user-1");
        result.set("X-Roles", "MEDIA_VIEWER,MEDIA_OPERATOR");
        return result;
    }

    /** 仅装配真实 HTTP 边界；设备、存储和媒体 SDK 由替身隔离。 */
    @Configuration
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
    @Import({VideoSessionController.class, FixedCameraIngressController.class, FixedCameraSourceController.class,
            FieldCallController.class, LiveKitWebhookController.class, RobotTtsController.class,
            FileController.class, com.robot.mediaserver.file.progress.InternalFileProgressController.class,
            com.robot.mediaserver.file.progress.FileProgressAuthentication.class,
            CurrentUserResolver.class, DateTimeConfig.class, ApiExceptionHandler.class, FileOpenApiConfig.class})
    static class Application {
        @Bean MediaProperties properties() { return new MediaProperties(); }
    }
}
