package com.robot.control.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.robot.control.auth.CurrentUserResolver;
import com.robot.control.call.IntercomBusyException;
import com.robot.control.call.IntercomCallService;
import com.robot.control.client.ControlManagementClient;
import com.robot.control.client.ControlMediaServiceClient;
import com.robot.control.config.ControlServiceProperties;
import com.robot.control.config.DateTimeConfig;
import com.robot.control.config.FileOpenApiConfig;
import com.robot.control.fixedcamera.FixedCameraCatalogLeaseService;
import com.robot.control.fixedcamera.FixedCameraCatalogSnapshot;
import com.robot.control.fixedcamera.FixedCameraHealthService;
import com.robot.control.fixedcamera.FixedCameraPublisherLifecycleService;
import com.robot.control.robot.service.RobotRegistryService;
import com.robot.control.service.ControlVideoCommandService;
import com.robot.control.service.EquipmentControlService;
import com.robot.control.service.MultiFunctionAudioTransferService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** 覆盖剩余 Control 映射；实际消费 Media 样例，并验证可信身份、租约时间及业务冲突结构。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = ServiceOpenApiContractTest.Application.class,
        properties = {"springdoc.api-docs.enabled=true", "spring.main.banner-mode=off",
                "control.media-service-base-url=http://media-service"})
class ServiceOpenApiContractTest {
    @Autowired private TestRestTemplate http;
    @Autowired private ObjectMapper mapper;
    @Autowired private RequestMappingHandlerMapping mappings;
    @Autowired private MockRestServiceServer media;
    @MockitoBean private ControlVideoCommandService videos;
    @MockitoBean private EquipmentControlService equipment;
    @MockitoBean private IntercomCallService intercom;
    @MockitoBean private MultiFunctionAudioTransferService audio;
    @MockitoBean private RobotRegistryService registry;
    @MockitoBean private ControlManagementClient management;
    @MockitoBean private FixedCameraHealthService health;
    @MockitoBean private FixedCameraCatalogLeaseService leases;
    @MockitoBean private FixedCameraPublisherLifecycleService publishers;
    @MockitoBean private com.robot.control.mileage.MileageService mileage;
    private JsonNode contract;
    private FileContractSupport support;

    @BeforeEach
    void setUp() throws Exception {
        media.reset();
        contract = mapper.readTree(http.getForObject("/v3/api-docs/service", String.class));
        support = new FileContractSupport(mapper, contract);
    }

    @Test
    void keepsDefaultMileageContractStableWithAllControllersLoaded() throws Exception {
        JsonNode actual = mapper.readTree(http.getForObject("/v3/api-docs", String.class));
        assertThat(actual).isEqualTo(mapper.readTree(Path.of("../quality/openapi/control-mileage.json").toFile()));
    }

    @Test
    void coversAllRemainingMappingsAndKeepsSnapshot() throws Exception {
        Set<String> actual = new HashSet<>();
        mappings.getHandlerMethods().forEach((mapping, handler) -> {
            if (handler.getBeanType().getPackageName().startsWith("com.robot.control")
                    && handler.getBeanType() != ControlFileController.class
                    && handler.getBeanType() != com.robot.control.mileage.MileageController.class) {
                mapping.getPatternValues().forEach(path -> mapping.getMethodsCondition().getMethods()
                        .forEach(method -> actual.add(method.name().toLowerCase() + " " + path)));
            }
        });
        Set<String> documented = new HashSet<>();
        contract.path("paths").properties().forEach(path -> path.getValue().properties()
                .forEach(operation -> documented.add(operation.getKey() + " " + path.getKey())));
        assertThat(documented).containsExactlyInAnyOrderElementsOf(actual).hasSize(34);
        support.snapshot("control-service.json", contract);
    }

    @Test
    void consumesRealMediaVideoFixtureThroughClientAndHttpController() throws Exception {
        String fixture = Files.readString(Path.of("../quality/openapi/fixtures/video-session.json"));
        media.expect(requestTo("http://media-service/internal/media/video-sessions"))
                .andExpect(header("X-User-Id", "user-1"))
                .andRespond(withSuccess("[" + fixture + "]", MediaType.APPLICATION_JSON));
        when(videos.filterAuthorizedSessions(any())).thenAnswer(invocation -> invocation.getArgument(0));
        JsonNode body = json("get", "/api/control/video-sessions", "/api/control/video-sessions", null, headers(), 200);
        assertThat(body.get(0)).isEqualTo(mapper.readTree(fixture));
        media.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {404, 503})
    void preservesUpstreamErrorsAndRejectsIncompleteMediaErrors(int status) throws Exception {
        String path = "/api/control/video-sessions";
        ObjectNode upstream = mapper.createObjectNode()
                .put("timestamp", "2026-09-30T12:00:00+08:00")
                .put("status", status)
                .put("code", status == 404 ? "VIDEO_SESSION_NOT_FOUND" : "MEDIA_SERVICE_UNAVAILABLE")
                .put("message", status == 404 ? "视频会话不存在" : "媒体服务暂不可用")
                .put("retryable", status == 503)
                .put("requestId", "contract-error-1")
                .put("path", "/internal/media/video-sessions");
        media.expect(requestTo("http://media-service/internal/media/video-sessions"))
                .andRespond(withStatus(HttpStatus.valueOf(status)).contentType(MediaType.APPLICATION_JSON)
                        .body(upstream.toString()));
        assertThat(json("get", path, path, null, headers(), status)).isEqualTo(upstream);
        media.verify();

        // 透传错误不能因为同时含有本地三字段而绕过上游必填项及类型校验。
        ObjectNode missing = upstream.deepCopy();
        missing.remove("retryable");
        assertThatThrownBy(() -> support.validate(path, "get", status, missing)).isInstanceOf(AssertionError.class);
        ObjectNode invalid = upstream.deepCopy().put("status", "错误的字符串");
        assertThatThrownBy(() -> support.validate(path, "get", status, invalid)).isInstanceOf(AssertionError.class);
    }

    @Test
    void requiresUserAndRolesAndPreservesIntercomConflictCode() throws Exception {
        String path = "/api/control/robots/{robotId}/cameras/{deviceId}/video/start";
        String url = path.replace("{robotId}", "r").replace("{deviceId}", "d");
        json("post", path, url, null, new HttpHeaders(), 400);
        String intercomPath = path.replace("video/start", "video/intercom/start");
        when(videos.startIntercom(anyString(), anyString(), any(), any()))
                .thenThrow(new IntercomBusyException("ROBOT_INTERCOM_BUSY", "机器人正在对讲"));
        JsonNode body = json("post", intercomPath, url.replace("video/start", "video/intercom/start"),
                Map.of(), headers(), 409);
        assertThat(body.path("code").asText()).isEqualTo("ROBOT_INTERCOM_BUSY");
    }

    @Test
    void preservesCatalogRfc3339AndRequiresTrustedCaller() throws Exception {
        String path = "/internal/control/fixed-camera-catalog-leases";
        Instant time = Instant.parse("2026-09-29T04:00:00Z");
        when(leases.upsert(any())).thenReturn(new FixedCameraCatalogSnapshot("1", "gateway-1", 7, time,
                List.of(new FixedCameraCatalogSnapshot.CameraRecord("camera-1", true, "RTSP", "rtsp://camera.example/main", null, time))));
        Map<String, Object> body = Map.of("leaseId", "lease-1", "gatewayId", "gateway-1", "cameras", List.of());
        json("put", path, path, body, headers(), 403);
        HttpHeaders headers = headers();
        headers.set("X-Internal-Caller", "bigscreen-bff");
        JsonNode value = json("put", path, path, body, headers, 200);
        assertThat(value.path("issuedAt").asText()).isEqualTo("2026-09-29T04:00:00Z");
        assertThat(value.path("cameras").get(0).path("subStreamUrl").isNull()).isTrue();
        ResponseEntity<byte[]> released = request("delete", path + "/lease-1", null, headers);
        assertThat(released.getStatusCode().value()).isEqualTo(200);
        assertThat(released.getBody()).isNullOrEmpty();
        verify(leases).release("lease-1");
    }

    @Test
    void validatesPublisherRequestBeforeDispatch() throws Exception {
        String path = "/internal/control/fixed-cameras/{cameraId}/publisher-quiesce";
        HttpHeaders headers = headers();
        headers.set("X-Internal-Caller", "management-service");
        json("post", path, path.replace("{cameraId}", "camera-1"), Map.of("publisherRevision", 7, "reason", "INVALID"), headers, 400);
        String switchPath = path.replace("publisher-quiesce", "publisher-mode-switch");
        json("post", switchPath, switchPath.replace("{cameraId}", "camera-1"), Map.of("publisherRevision", 7), headers, 400);
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

    /** 真实 MVC、身份、异常、时间和客户端；网络及业务状态单独隔离。 */
    @Configuration
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
    @EnableConfigurationProperties(ControlServiceProperties.class)
    @Import({ControlRobotController.class, ControlVideoSessionController.class, ControlFixedCameraController.class,
            InternalFixedCameraCatalogController.class, InternalFixedCameraPublisherController.class,
            ControlFileController.class, com.robot.control.mileage.MileageController.class,
            com.robot.control.config.MileageOpenApiConfig.class,
            CurrentUserResolver.class, DateTimeConfig.class, ApiExceptionHandler.class, FileOpenApiConfig.class})
    static class Application {
        @Bean RestClient.Builder mediaBuilder(ObjectMapper mapper) {
            return RestClient.builder().messageConverters(converters -> {
                converters.removeIf(MappingJackson2HttpMessageConverter.class::isInstance);
                converters.add(new MappingJackson2HttpMessageConverter(mapper));
            });
        }
        @Bean MockRestServiceServer mediaServer(RestClient.Builder builder) {
            return MockRestServiceServer.bindTo(builder).build();
        }
        @Bean ControlMediaServiceClient mediaClient(
                ControlServiceProperties properties, RestClient.Builder builder, MockRestServiceServer mediaServer) {
            return new ControlMediaServiceClient(properties, builder);
        }
    }
}
