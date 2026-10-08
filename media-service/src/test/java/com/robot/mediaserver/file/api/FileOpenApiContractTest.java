package com.robot.mediaserver.file.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.robot.media.common.file.FileBatchDeleteResponse;
import com.robot.media.common.file.FileDeleteResultResponse;
import com.robot.media.common.file.FileDownloadUrlResponse;
import com.robot.media.common.file.FileListItemResponse;
import com.robot.media.common.file.FileListResponse;
import com.robot.media.common.file.FilePlayUrlResponse;
import com.robot.mediaserver.auth.CurrentUserResolver;
import com.robot.mediaserver.config.DateTimeConfig;
import com.robot.mediaserver.config.MediaProperties;
import com.robot.mediaserver.file.dto.FilePartUploadUrlResponse;
import com.robot.mediaserver.file.dto.FilePartUrlsResponse;
import com.robot.mediaserver.file.dto.FileStatusResponse;
import com.robot.mediaserver.file.dto.FileUploadResponse;
import com.robot.mediaserver.file.progress.FileProgressAuthentication;
import com.robot.mediaserver.file.progress.FileProgressPhase;
import com.robot.mediaserver.file.progress.FileUploadProgressItem;
import com.robot.mediaserver.file.progress.FileUploadProgressQueryResponse;
import com.robot.mediaserver.file.progress.FileUploadProgressService;
import com.robot.mediaserver.file.progress.InternalFileProgressController;
import com.robot.mediaserver.file.service.FileMultipartCompletionService;
import com.robot.mediaserver.file.service.FileService;
import com.robot.mediaserver.file.service.FileStorageException;
import com.robot.mediaserver.video.api.ApiExceptionHandler;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.LinkedMultiValueMap;

/** 真实 HTTP 验证 30 条文件映射、参数绑定、错误边界和序列化；存储与业务服务由测试替身隔离。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = FileOpenApiContractTest.Application.class,
        properties = {"springdoc.api-docs.enabled=true", "spring.main.banner-mode=off"})
class FileOpenApiContractTest {
    private static final OffsetDateTime TIME = OffsetDateTime.parse("2026-09-29T12:00:00+08:00");
    @Autowired private TestRestTemplate http;
    @Autowired private ObjectMapper mapper;
    @MockitoBean private FileService files;
    @MockitoBean private FileMultipartCompletionService completion;
    @MockitoBean private FileUploadProgressService progress;
    @Autowired private MediaProperties properties;
    private JsonNode contract;
    private FileContractSupport support;

    @BeforeEach
    void setUp() throws Exception {
        properties.getFile().setProgressWebhookToken("contract-internal-token");
        properties.getFile().setTrustedRobotNetworkEnabled(true);
        properties.getFile().setTrustedRobotCidrs("127.0.0.1/32,::1/128");
        contract = mapper.readTree(http.getForObject("/v3/api-docs", String.class));
        support = new FileContractSupport(mapper, contract);
        FileListItemResponse item = new FileListItemResponse("file-1", "robot-1", null, null, "IMAGE", "sample.png",
                "image/png", 3L, null, null, null, null, null, "READY", null, null, TIME, TIME, null, null);
        when(files.uploadSimple(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(item);
        when(files.list(any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new FileListResponse(List.of(item), 0, 20, 1));
        when(files.detail(any(), anyString())).thenReturn(item);
        when(files.createOrResumeMultipart(anyString(), any())).thenReturn(new FileUploadResponse("file-1", "upl-1",
                "MULTIPART", "UPLOADING", 5242880, 1, List.of(), List.of(new FilePartUploadUrlResponse(1, "https://storage.example/part")), TIME));
        when(files.partUrls(anyString(), anyString(), any())).thenReturn(new FilePartUrlsResponse(TIME,
                List.of(new FilePartUploadUrlResponse(1, "https://storage.example/part"))));
        FileStatusResponse status = new FileStatusResponse("file-1", "READY", 3, true, null, null, TIME);
        when(files.fileStatus(anyString(), anyString())).thenReturn(status);
        when(completion.complete(anyString(), anyString())).thenReturn(status);
        when(files.deleteBatch(any(), any())).thenReturn(new FileBatchDeleteResponse(1, 1, 0,
                List.of(new FileDeleteResultResponse("file-1", true, "DELETED", "删除成功"))));
        when(files.downloadUrl(any(), anyString(), anyBoolean())).thenReturn(new FileDownloadUrlResponse("file-1", "https://storage.example/download", TIME));
        when(files.playUrl(any(), anyString())).thenReturn(new FilePlayUrlResponse("file-1", "hls", "application/vnd.apple.mpegurl",
                "/api/media/files/file-1/hls/index.m3u8?token=fixture-token", TIME));
        when(files.content(any(), anyString())).thenReturn(new FileService.PlaybackAsset(new byte[]{1, 2, 3}, "image/png"));
        when(files.playbackAsset(anyString(), anyString(), anyString())).thenReturn(new FileService.PlaybackAsset(
                "#EXTM3U\n".getBytes(StandardCharsets.UTF_8), "application/vnd.apple.mpegurl"));
        when(progress.query(any())).thenReturn(new FileUploadProgressQueryResponse(List.of(new FileUploadProgressItem(
                "file-1", null, "sample.png", "IMAGE", FileProgressPhase.READY, 3, 3, 0, null, 100, true, 0, TIME, null, null)), List.of(), TIME));
    }

    @Test
    void generatesAllFileMappingsWithStableAliasIds() throws Exception {
        int operations = 0;
        List<String> ids = new ArrayList<>();
        for (JsonNode path : contract.path("paths")) {
            for (JsonNode operation : path) {
                if (operation.has("operationId")) {
                    operations++;
                    ids.add(operation.path("operationId").asText());
                }
            }
        }
        assertThat(operations).isEqualTo(30);
        assertThat(ids).doesNotHaveDuplicates();
        assertThat(contract.path("openapi").asText()).isEqualTo("3.1.0");
        JsonNode upload = contract.path("paths").path("/api/media/files").path("post");
        for (JsonNode parameter : upload.path("parameters")) {
            assertThat(parameter.path("name").asText()).isNotEqualTo("fileType");
        }
        assertThat(upload.path("requestBody").path("content").has("multipart/form-data")).isTrue();
        support.snapshot("media-files.json", contract);
    }

    @TestFactory
    Stream<DynamicTest> validatesEveryPublicAndInternalFileOperation() {
        List<Call> calls = new ArrayList<>();
        for (String prefix : List.of("/api/media/files", "/internal/media/files")) {
            calls.add(new Call("post", prefix, "", form(), 200, "file-upload"));
            calls.add(new Call("get", prefix, "?page=0&size=20", null, 200, "file-list"));
            calls.add(new Call("get", prefix + "/{fileId}", "", null, 200, "file-detail"));
            calls.add(new Call("delete", prefix + "/{fileId}", "", null, 204, null));
            calls.add(new Call("delete", prefix + "/batch", "", Map.of("fileIds", List.of("file-1")), 200, "file-delete-batch"));
            calls.add(new Call("post", prefix + "/{fileId}/download-url", "?inline=true", null, 200, "file-download"));
            calls.add(new Call("post", prefix + "/{fileId}/play-url", "", null, 200, "file-play"));
            calls.add(new Call("get", prefix + "/{fileId}/content", "", null, 200, null));
            calls.add(new Call("get", prefix + "/{fileId}/hls/{objectName}", "?token=fixture-token", null, 200, null));
            calls.add(new Call("post", prefix + "/multipart-uploads", "", Map.of("fileType", "IMAGE", "fileName", "sample.png", "contentType", "image/png", "fileSize", 3), 200, "file-multipart"));
            calls.add(new Call("post", prefix + "/multipart-uploads/{uploadId}/part-urls", "", Map.of("partNumbers", List.of(1)), 200, "file-parts"));
            calls.add(new Call("post", prefix + "/multipart-uploads/{uploadId}/complete", "", null, 200, "file-status"));
            calls.add(new Call("get", prefix + "/{fileId}/status", "", null, 200, "file-status"));
            calls.add(new Call("post", prefix + "/extension-binding", "", Map.of("extensionId", "extension-1", "fileIds", List.of("file-1")), 204, null));
        }
        calls.add(new Call("post", "/internal/media/files/upload-progress-queries", "", Map.of("fileIds", List.of("file-1")), 200, "file-progress"));
        calls.add(new Call("post", "/internal/media/file-upload-events/minio", "", Map.of("Records", List.of()), 204, null));
        return calls.stream().map(call -> DynamicTest.dynamicTest(call.method() + " " + call.path(), () -> {
            ResponseEntity<byte[]> response = request(call);
            assertThat(response.getStatusCode().value()).isEqualTo(call.status());
            if (call.fixture() != null) {
                assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
                JsonNode body = mapper.readTree(response.getBody());
                support.validate(call.path(), call.method(), call.status(), body);
                support.snapshot("fixtures/" + call.fixture() + ".json", body);
            } else if (call.status() == 204) {
                assertThat(response.getBody()).isNullOrEmpty();
            } else if (call.path().contains("/hls/")) {
                assertThat(response.getHeaders().getContentType().toString()).startsWith("application/vnd.apple.mpegurl");
                assertThat(response.getBody()).isEqualTo("#EXTM3U\n".getBytes(StandardCharsets.UTF_8));
            } else {
                assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
                assertThat(response.getBody()).containsExactly(1, 2, 3);
            }
        }));
    }

    @Test
    void validatesInputAndKeepsBusinessFrameworkAndNetworkErrorsDistinct() throws Exception {
        String path = "/api/media/files/multipart-uploads";
        Call invalid = new Call("post", path, "", Map.of("fileSize", 0), 400, null);
        assertError(invalid, "VALIDATION_ERROR");
        properties.getFile().setTrustedRobotCidrs("192.0.2.0/24");
        assertError(new Call("get", "/api/media/files/{fileId}/status", "", null, 403, null), "UNTRUSTED_ROBOT_NETWORK");
        properties.getFile().setTrustedRobotCidrs("127.0.0.1/32,::1/128");
        when(files.detail(any(), anyString())).thenThrow(new FileApiException(HttpStatus.NOT_FOUND, "FILE_NOT_FOUND", "未找到文件"));
        assertError(new Call("get", "/api/media/files/{fileId}", "", null, 404, null), "FILE_NOT_FOUND");
        when(files.createOrResumeMultipart(anyString(), any())).thenThrow(new FileApiException(HttpStatus.TOO_MANY_REQUESTS,
                "UPLOAD_SESSION_LIMIT", "超过会话上限", true, Map.of("limit", 1)));
        ResponseEntity<byte[]> limited = assertError(new Call("post", path, "", Map.of("fileType", "IMAGE", "fileName", "a.png", "contentType", "image/png", "fileSize", 1), 429, null), "UPLOAD_SESSION_LIMIT");
        assertThat(limited.getHeaders().getFirst("Retry-After")).isEqualTo("60");
        assertThat(limited.getHeaders().getFirst("X-Request-Id")).isNotBlank();
        when(files.content(any(), anyString())).thenThrow(new FileStorageException("test storage failure", new IllegalStateException()));
        assertError(new Call("get", "/api/media/files/{fileId}/content", "", null, 503, null), "STORAGE_UNAVAILABLE");
        assertError(new Call("get", "/api/media/files", "?page=not-an-integer", null, 404, null), "NOT_FOUND");
    }

    @Test
    void callbackRequiresConfiguredInternalCredential() throws Exception {
        properties.getFile().setProgressWebhookToken("different-token");
        assertError(new Call("post", "/internal/media/file-upload-events/minio", "", Map.of("Records", List.of()), 401, null), "INTERNAL_TOKEN_INVALID");
        properties.getFile().setProgressWebhookToken("");
        assertError(new Call("post", "/internal/media/file-upload-events/minio", "", Map.of("Records", List.of()), 503, null), "INTERNAL_TOKEN_NOT_CONFIGURED");
    }

    @Test
    void schemaRejectsMissingRequiredFieldsAndWrongNullableTypes() throws Exception {
        ObjectNode body = (ObjectNode) mapper.readTree(request(new Call("get", "/api/media/files/{fileId}", "", null, 200, null)).getBody());
        body.remove("fileId");
        assertThatThrownBy(() -> support.validate("/api/media/files/{fileId}", "get", 200, body)).isInstanceOf(AssertionError.class);
        body.put("fileId", "file-1");
        body.put("durationSeconds", "unknown");
        assertThatThrownBy(() -> support.validate("/api/media/files/{fileId}", "get", 200, body)).isInstanceOf(AssertionError.class);
    }

    private ResponseEntity<byte[]> assertError(Call call, String code) throws Exception {
        ResponseEntity<byte[]> response = request(call);
        assertThat(response.getStatusCode().value()).isEqualTo(call.status());
        JsonNode body = mapper.readTree(response.getBody());
        support.validate(call.path(), call.method(), call.status(), body);
        if (code != null) {
            assertThat(body.path("code").asText()).isEqualTo(code);
        }
        return response;
    }

    private ResponseEntity<byte[]> request(Call call) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.ALL));
        headers.set("X-Robot-Id", "robot-1");
        headers.setBearerAuth("contract-internal-token");
        if (call.body() != null) {
            headers.setContentType(call.body() instanceof LinkedMultiValueMap<?, ?> ? MediaType.MULTIPART_FORM_DATA : MediaType.APPLICATION_JSON);
        }
        String path = call.path().replace("{fileId}", "file-1").replace("{uploadId}", "upl-1").replace("{objectName}", "index.m3u8");
        return http.exchange(path + call.query(), HttpMethod.valueOf(call.method().toUpperCase(java.util.Locale.ROOT)), new HttpEntity<>(call.body(), headers), byte[].class);
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
     * @param path 请求路径模板
     * @param query 查询串
     * @param body 本次请求正文，允许为空
     * @param status 预期 HTTP 状态码
     * @param fixture 响应样例名称
     */
    private record Call(String method, String path, String query, Object body, int status, String fixture) {
    }

    /** 只装配 HTTP、实际参数校验/异常处理及网络过滤；不连接存储、MQTT 或数据库。 */
    @Configuration
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
    @Import({FileController.class, InternalFileProgressController.class, TrustedRobotNetworkFilter.class,
            CurrentUserResolver.class, DateTimeConfig.class, ApiExceptionHandler.class,
            FileProgressAuthentication.class, FileOpenApiConfig.class})
    static class Application {
        @Bean MediaProperties properties() { return new MediaProperties(); }
    }
}
