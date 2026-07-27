package com.robot.control.api;

import com.robot.control.call.IntercomBusyException;
import com.robot.control.config.DateTimeConfig;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;

/** Preserves downstream Media Service failures at the public Control API boundary. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IntercomBusyException.class)
    public ResponseEntity<Map<String, Object>> handleIntercomBusy(IntercomBusyException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "timestamp", DateTimeConfig.format(OffsetDateTime.now()),
                "code", ex.code(),
                "message", ex.getMessage()));
    }

    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<String> handleUpstreamResponse(RestClientResponseException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ex.getResponseBodyAsString());
    }
}
