package com.robot.mediaserver.file.progress;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/media")
public class InternalFileProgressController {

    private final FileUploadProgressService progressService;
    private final FileProgressAuthentication authentication;

    public InternalFileProgressController(
            FileUploadProgressService progressService,
            FileProgressAuthentication authentication) {
        this.progressService = progressService;
        this.authentication = authentication;
    }

    @PostMapping("/files/upload-progress-queries")
    public FileUploadProgressQueryResponse query(@Valid @RequestBody FileUploadProgressQueryRequest request) {
        return progressService.query(request.fileIds());
    }

    @PostMapping("/file-upload-events/minio")
    public ResponseEntity<Void> minioEvent(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody JsonNode payload) {
        authentication.requireWebhookToken(authorization);
        progressService.acceptMinioEvent(payload);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
