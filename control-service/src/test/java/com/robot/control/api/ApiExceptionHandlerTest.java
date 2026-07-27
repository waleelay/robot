package com.robot.control.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.robot.control.call.IntercomBusyException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void preservesDownstreamConflictStatusAndBody() {
        String body = """
                {"message":"当前用户未持有对讲权限","code":"INVALID_STATE"}
                """.trim();
        HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatus.CONFLICT,
                "Conflict",
                HttpHeaders.EMPTY,
                body.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);

        ResponseEntity<String> response = handler.handleUpstreamResponse(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isEqualTo(body);
    }

    @Test
    void mapsIntercomBusyToFriendlyBusinessConflict() {
        ResponseEntity<java.util.Map<String, Object>> response = handler.handleIntercomBusy(
                new IntercomBusyException("CLIENT_BUSY", "当前终端正在与其他机器人通话，请先结束当前通话"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody())
                .containsEntry("code", "CLIENT_BUSY")
                .containsEntry("message", "当前终端正在与其他机器人通话，请先结束当前通话");
    }
}
