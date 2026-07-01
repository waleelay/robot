package com.robot.mediaserver.control.api;

import com.robot.mediaserver.auth.CurrentUserResolver;
import com.robot.mediaserver.control.client.ControlMediaServiceClient;
import com.robot.mediaserver.file.dto.FileDownloadUrlResponse;
import com.robot.mediaserver.file.dto.FileListItemResponse;
import com.robot.mediaserver.file.dto.FileListResponse;
import com.robot.mediaserver.file.dto.FilePlayUrlResponse;
import com.robot.mediaserver.file.model.FileStatus;
import com.robot.mediaserver.file.model.FileType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/control/files")
public class ControlFileController {

    private final ControlMediaServiceClient mediaServiceClient;
    private final CurrentUserResolver currentUserResolver;

    public ControlFileController(ControlMediaServiceClient mediaServiceClient, CurrentUserResolver currentUserResolver) {
        this.mediaServiceClient = mediaServiceClient;
        this.currentUserResolver = currentUserResolver;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FileListItemResponse uploadSimple(
            @RequestPart("file") MultipartFile file,
            @RequestParam FileType fileType,
            @RequestParam(required = false) String robotId,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String taskExecutionId,
            @RequestParam(required = false) String sourceFileId,
            @RequestParam(required = false) String metadata,
            HttpServletRequest request) {
        return mediaServiceClient.uploadSimpleFile(
                file,
                fileType,
                robotId,
                deviceId,
                taskExecutionId,
                sourceFileId,
                metadata,
                currentUserResolver.resolve(request));
    }

    @GetMapping
    public FileListResponse list(
            @RequestParam(required = false) String robotId,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String taskExecutionId,
            @RequestParam(required = false) FileType fileType,
            @RequestParam(required = false) FileStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        return mediaServiceClient.files(
                robotId,
                deviceId,
                taskExecutionId,
                fileType,
                status,
                page,
                size,
                currentUserResolver.resolve(request));
    }

    @GetMapping("/{fileId}")
    public FileListItemResponse detail(@PathVariable String fileId, HttpServletRequest request) {
        return mediaServiceClient.file(fileId, currentUserResolver.resolve(request));
    }

    @PostMapping("/{fileId}/download-url")
    public FileDownloadUrlResponse downloadUrl(@PathVariable String fileId, HttpServletRequest request) {
        return mediaServiceClient.fileDownloadUrl(fileId, currentUserResolver.resolve(request));
    }

    @GetMapping("/{fileId}/content")
    public ResponseEntity<byte[]> content(@PathVariable String fileId, HttpServletRequest request) {
        return mediaServiceClient.fileContent(fileId, currentUserResolver.resolve(request));
    }

    @PostMapping("/{fileId}/play-url")
    public FilePlayUrlResponse playUrl(@PathVariable String fileId, HttpServletRequest request) {
        return mediaServiceClient.filePlayUrl(fileId, currentUserResolver.resolve(request));
    }

    @GetMapping("/{fileId}/hls/{objectName}")
    public ResponseEntity<byte[]> hls(
            @PathVariable String fileId,
            @PathVariable String objectName,
            @RequestParam String token) {
        byte[] body = mediaServiceClient.fileHlsAsset(fileId, objectName, token);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(hlsContentType(objectName)))
                .body(body);
    }

    private String hlsContentType(String objectName) {
        if (objectName.endsWith(".m3u8")) {
            return "application/vnd.apple.mpegurl";
        }
        if (objectName.endsWith(".m4s") || objectName.endsWith(".mp4")) {
            return "video/mp4";
        }
        if (objectName.endsWith(".ts")) {
            return "video/mp2t";
        }
        return "application/octet-stream";
    }
}
