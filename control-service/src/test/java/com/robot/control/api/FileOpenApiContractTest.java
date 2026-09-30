package com.robot.control.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.robot.control.auth.CurrentUserResolver;
import com.robot.control.client.ControlMediaServiceClient;
import com.robot.control.config.ControlServiceProperties;
import com.robot.control.config.DateTimeConfig;
import com.robot.control.config.FileOpenApiConfig;
import com.robot.control.config.MileageOpenApiConfig;
import com.robot.control.mileage.MileageController;
import com.robot.control.mileage.MileageService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

/** 用 Media 的真实 HTTP 序列化样例验证 Control 客户端、代理与文件契约，隔离远端网络。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = FileOpenApiContractTest.Application.class,
        properties = {"springdoc.api-docs.enabled=true", "spring.main.banner-mode=off",
                "control.media-service-base-url=http://media-service"})
class FileOpenApiContractTest {
    private static final String PREFIX = "/api/control/files";
    @Autowired private TestRestTemplate http;
    @Autowired private ObjectMapper mapper;
    @Autowired private MockRestServiceServer media;
    @MockitoBean private MileageService mileage;
    private JsonNode contract;
    private FileContractSupport support;

    @BeforeEach
    void setUp() throws Exception {
        media.reset();
        String text = http.getForObject("/v3/api-docs/files", String.class);
        contract = mapper.readTree(text);
        support = new FileContractSupport(mapper, contract);
    }

    @Test
    void generatesNineOperationsWithoutExpandingDefaultMileageDocument() throws Exception {
        int operations = 0;
        for (JsonNode path : contract.path("paths")) {
            for (JsonNode operation : path) {
                if (operation.has("operationId")) {
                    operations++;
                }
            }
        }
        assertThat(operations).isEqualTo(9);
        JsonNode upload = contract.path("paths").path("/api/control/files").path("post");
        for (JsonNode parameter : upload.path("parameters")) {
            assertThat(parameter.path("name").asText()).isNotEqualTo("fileType");
        }
        assertThat(upload.path("requestBody").path("content").has("multipart/form-data")).isTrue();
        support.snapshot("control-files.json", contract);
        JsonNode mileageDocument = mapper.readTree(http.getForObject("/v3/api-docs", String.class));
        assertThat(mileageDocument.path("paths").size()).isEqualTo(1);
        assertThat(mileageDocument.path("paths").has("/api/control/statistics/mileage")).isTrue();
    }

    /** 使用真实 Control HTTP 客户端验证九个文件操作的路径、身份头、正文和 Media 样例兼容性。 */
    @TestFactory
    Stream<DynamicTest> forwardsAllNineOperationsAndValidatesMediaFixtures() {
        return List.of(
                new Call("POST", "", "", form(), "file-upload", 200, "application/json"),
                new Call("GET", "", "?page=0&size=20", null, "file-list", 200, "application/json"),
                new Call("GET", "/{fileId}", "", null, "file-detail", 200, "application/json"),
                new Call("DELETE", "/{fileId}", "", null, null, 204, null),
                new Call("DELETE", "/batch", "", Map.of("fileIds", List.of("file-1")), "file-delete-batch", 200, "application/json"),
                new Call("POST", "/{fileId}/download-url", "?inline=true", null, "file-download", 200, "application/json"),
                new Call("POST", "/{fileId}/play-url", "", null, "file-play", 200, "application/json"),
                new Call("GET", "/{fileId}/content", "", null, null, 200, "image/png"),
                new Call("GET", "/{fileId}/hls/{objectName}", "?token=fixture-token", null, null, 200, "application/vnd.apple.mpegurl")
        ).stream().map(call -> DynamicTest.dynamicTest(call.method() + " " + call.suffix(), () -> {
            media.reset();
            byte[] upstream = call.fixture() == null ? new byte[]{1, 2, 3}
                    : Files.readAllBytes(Path.of("../quality/openapi/fixtures/" + call.fixture() + ".json"));
            String suffix = call.suffix().replace("{fileId}", "file-1").replace("{objectName}", "index.m3u8");
            var expectation = media.expect(requestTo("http://media-service/internal/media/files" + suffix + call.query()))
                    .andExpect(method(HttpMethod.valueOf(call.method())));
            if (!call.suffix().contains("/hls/")) {
                expectation.andExpect(header("X-User-Id", "user-1")).andExpect(header("X-Roles",
                        org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.containsString("MEDIA_VIEWER"),
                                org.hamcrest.Matchers.containsString("MEDIA_OPERATOR"))));
            }
            if (call.status() == 204) {
                expectation.andRespond(withStatus(HttpStatus.NO_CONTENT));
            } else {
                expectation.andRespond(withSuccess(upstream, MediaType.parseMediaType(call.contentType())));
            }
            ResponseEntity<byte[]> response = request(call, true);
            assertThat(response.getStatusCode().value()).isEqualTo(call.status());
            if (call.fixture() != null) {
                JsonNode body = mapper.readTree(response.getBody());
                JsonNode expected = mapper.readTree(upstream);
                if ("file-play".equals(call.fixture())) {
                    ((ObjectNode) expected).put("playUrl", expected.path("playUrl").asText().replace("/api/media/files/", "/api/control/files/"));
                }
                assertThat(body).isEqualTo(expected);
                support.validate(PREFIX + call.suffix(), call.method().toLowerCase(java.util.Locale.ROOT), call.status(), body);
            } else if (call.status() == 204) {
                assertThat(response.getBody()).isNullOrEmpty();
            } else {
                assertThat(response.getBody()).isEqualTo(upstream);
                assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.parseMediaType(call.contentType()));
            }
            media.verify();
        }));
    }

    @Test
    void requiresTrustedIdentityAndPreservesDownstreamErrorBody() throws Exception {
        Call call = new Call("GET", "/{fileId}", "", null, null, 400, null);
        ResponseEntity<byte[]> denied = request(call, false);
        assertThat(denied.getStatusCode().value()).isEqualTo(400);
        support.validate(PREFIX + call.suffix(), "get", 400, mapper.readTree(denied.getBody()));
        media.verify();
        String body = """
                {"timestamp":"2026-09-29 12:00:00","status":404,"code":"FILE_NOT_FOUND",
                 "message":"未找到文件","retryable":false,"requestId":"req-test","path":"/internal/media/files/file-1"}
                """;
        media.expect(requestTo("http://media-service/internal/media/files/file-1"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON).body(body));
        ResponseEntity<byte[]> missing = request(call, true);
        assertThat(missing.getStatusCode().value()).isEqualTo(404);
        assertThat(mapper.readTree(missing.getBody())).isEqualTo(mapper.readTree(body));
        support.validate(PREFIX + call.suffix(), "get", 404, mapper.readTree(missing.getBody()));
        media.verify();
    }

    private ResponseEntity<byte[]> request(Call call, boolean identity) {
        HttpHeaders headers = new HttpHeaders();
        if (identity) {
            headers.set("X-User-Id", "user-1");
            headers.set("X-Org-Id", "org-1");
            headers.set("X-Roles", "MEDIA_VIEWER,MEDIA_OPERATOR");
        }
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.ALL));
        if (call.body() != null) {
            headers.setContentType(call.body() instanceof LinkedMultiValueMap<?, ?> ? MediaType.MULTIPART_FORM_DATA : MediaType.APPLICATION_JSON);
        }
        return http.exchange(PREFIX + call.suffix().replace("{fileId}", "file-1").replace("{objectName}", "index.m3u8") + call.query(),
                HttpMethod.valueOf(call.method()), new HttpEntity<>(call.body(), headers), byte[].class);
    }

    private LinkedMultiValueMap<String, Object> form() {
        LinkedMultiValueMap<String, Object> result = new LinkedMultiValueMap<>();
        result.add("fileType", "IMAGE");
        result.add("file", new ByteArrayResource(new byte[]{1, 2, 3}) {
            @Override public String getFilename() { return "sample.png"; }
        });
        return result;
    }

    /** 文件契约测试中的单次 HTTP 调用及预期结果。
     * @param method HTTP 方法
     * @param suffix 文件代理路径后缀
     * @param query 查询串
     * @param body 本次请求正文，允许为空
     * @param fixture 响应样例名称
     * @param status 预期 HTTP 状态码
     * @param contentType 预期响应媒体类型
     */
    private record Call(String method, String suffix, String query, Object body, String fixture, int status, String contentType) {
    }

    /** 使用真实 MVC、身份解析、异常处理和 Media 客户端；仅替换客户端的网络传输。 */
    @Configuration
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
    @EnableConfigurationProperties(ControlServiceProperties.class)
    @Import({ControlFileController.class, CurrentUserResolver.class, ApiExceptionHandler.class,
            DateTimeConfig.class, FileOpenApiConfig.class, MileageOpenApiConfig.class, MileageController.class})
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
            // 显式依赖 mediaServer，确保先绑定传输替身，再构建客户端。
            return new ControlMediaServiceClient(properties, builder);
        }
    }
}
