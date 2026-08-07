package com.robot.mediaserver.video.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.robot.mediaserver.file.api.FileApiException;
import com.robot.mediaserver.file.service.FileStorageException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void exposesStructuredUploadSessionLimitResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/media/files/multipart-uploads");
        request.addHeader("X-Request-Id", "req-test");
        FileApiException exception = new FileApiException(
                HttpStatus.TOO_MANY_REQUESTS,
                "UPLOAD_SESSION_LIMIT",
                "机器人未完成上传会话数量达到上限，请稍后重试",
                true,
                Map.of("scope", "ROBOT", "robotId", "test115", "activeCount", 20L, "limit", 20));

        var response = handler.handleFileApi(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("60");
        assertThat(response.getHeaders().getFirst("X-Request-Id")).isEqualTo("req-test");
        assertThat(response.getBody()).containsEntry("code", "UPLOAD_SESSION_LIMIT");
        assertThat(response.getBody()).containsEntry("retryable", true);
        assertThat(response.getBody()).containsEntry("requestId", "req-test");
        assertThat(response.getBody()).containsEntry("path", "/api/media/files/multipart-uploads");
        assertThat(response.getBody()).containsEntry("details", exception.getDetails());
    }

    @Test
    void mapsStorageFailureToRetryableServiceUnavailable() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/media/files");

        var response = handler.handleFileStorage(new FileStorageException("准备文件存储桶失败"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("5");
        assertThat(response.getBody()).containsEntry("code", "STORAGE_UNAVAILABLE");
        assertThat(response.getBody()).containsEntry("message", "对象存储暂不可用，请稍后重试");
        assertThat(response.getBody()).containsEntry("retryable", true);
    }
}
