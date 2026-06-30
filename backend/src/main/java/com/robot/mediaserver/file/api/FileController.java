package com.robot.mediaserver.file.api;

import com.robot.mediaserver.auth.CurrentUser;
import com.robot.mediaserver.auth.CurrentUserResolver;
import com.robot.mediaserver.file.dto.CreateMultipartFileUploadRequest;
import com.robot.mediaserver.file.dto.FileDownloadUrlResponse;
import com.robot.mediaserver.file.dto.FileListItemResponse;
import com.robot.mediaserver.file.dto.FileListResponse;
import com.robot.mediaserver.file.dto.FilePartUrlsRequest;
import com.robot.mediaserver.file.dto.FilePartUrlsResponse;
import com.robot.mediaserver.file.dto.FilePlayUrlResponse;
import com.robot.mediaserver.file.dto.FileStatusResponse;
import com.robot.mediaserver.file.dto.FileUploadResponse;
import com.robot.mediaserver.file.model.FileStatus;
import com.robot.mediaserver.file.model.FileType;
import com.robot.mediaserver.file.service.FileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping({"/internal/media/files", "/api/media/files"})
public class FileController {

    private final FileService service;
    private final CurrentUserResolver currentUserResolver;

    public FileController(FileService service, CurrentUserResolver currentUserResolver) {
        this.service = service;
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
        return service.uploadSimple(
                currentUserResolver.resolve(request),
                file,
                fileType,
                robotId,
                deviceId,
                taskExecutionId,
                sourceFileId,
                metadata);
    }

    @PostMapping("/multipart-uploads")
    public FileUploadResponse createMultipart(
            @RequestHeader(value = "X-Robot-Id", required = false) String robotId,
            @Valid @RequestBody CreateMultipartFileUploadRequest request) {
        return service.createOrResumeMultipart(robotId, request);
    }

    @PostMapping("/multipart-uploads/{uploadId}/part-urls")
    public FilePartUrlsResponse partUrls(
            @RequestHeader(value = "X-Robot-Id", required = false) String robotId,
            @PathVariable String uploadId,
            @Valid @RequestBody FilePartUrlsRequest request) {
        return service.partUrls(robotId, uploadId, request.getPartNumbers());
    }

    @PostMapping("/multipart-uploads/{uploadId}/complete")
    public FileStatusResponse complete(
            @RequestHeader(value = "X-Robot-Id", required = false) String robotId,
            @PathVariable String uploadId) {
        return service.completeMultipart(robotId, uploadId);
    }

    @GetMapping("/{fileId}/status")
    public FileStatusResponse status(
            @RequestHeader(value = "X-Robot-Id", required = false) String robotId,
            @PathVariable String fileId) {
        return service.fileStatus(robotId, fileId);
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
        return service.list(currentUserResolver.resolve(request), robotId, deviceId, taskExecutionId, fileType, status, page, size);
    }

    @GetMapping("/{fileId}")
    public FileListItemResponse detail(@PathVariable String fileId, HttpServletRequest request) {
        return service.detail(currentUserResolver.resolve(request), fileId);
    }

    @PostMapping("/{fileId}/download-url")
    public FileDownloadUrlResponse downloadUrl(@PathVariable String fileId, HttpServletRequest request) {
        return service.downloadUrl(currentUserResolver.resolve(request), fileId);
    }

    @GetMapping("/{fileId}/content")
    public ResponseEntity<byte[]> content(@PathVariable String fileId, HttpServletRequest request) {
        FileService.PlaybackAsset asset = service.content(currentUserResolver.resolve(request), fileId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(asset.contentType()))
                .body(asset.bytes());
    }

    @PostMapping("/{fileId}/play-url")
    public FilePlayUrlResponse playUrl(@PathVariable String fileId, HttpServletRequest request) {
        return service.playUrl(currentUserResolver.resolve(request), fileId);
    }

    @GetMapping("/{fileId}/hls/{objectName}")
    public ResponseEntity<byte[]> hls(
            @PathVariable String fileId,
            @PathVariable String objectName,
            @RequestParam String token) {
        FileService.PlaybackAsset asset = service.playbackAsset(fileId, objectName, token);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(asset.contentType()))
                .body(asset.bytes());
    }
}
