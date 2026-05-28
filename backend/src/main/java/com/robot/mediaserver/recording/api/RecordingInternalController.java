package com.robot.mediaserver.recording.api;

import com.robot.mediaserver.auth.CurrentUserResolver;
import com.robot.mediaserver.recording.dto.PlaybackUrlResponse;
import com.robot.mediaserver.recording.dto.RecordingListResponse;
import com.robot.mediaserver.recording.model.RecordingStatus;
import com.robot.mediaserver.recording.service.RecordingService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/media/recordings")
public class RecordingInternalController {

    private final RecordingService service;
    private final CurrentUserResolver currentUserResolver;

    public RecordingInternalController(RecordingService service, CurrentUserResolver currentUserResolver) {
        this.service = service;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping
    public RecordingListResponse list(
            @RequestParam(required = false) String robotId,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) RecordingStatus status,
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        return service.list(currentUserResolver.resolve(request), robotId, deviceId, status, from, to, page, size);
    }

    @PostMapping("/{recordingId}/play-url")
    public PlaybackUrlResponse playUrl(@PathVariable String recordingId, HttpServletRequest request) {
        return service.playUrl(currentUserResolver.resolve(request), recordingId);
    }

    @GetMapping("/{recordingId}/hls/{objectName}")
    public ResponseEntity<byte[]> hls(
            @PathVariable String recordingId,
            @PathVariable String objectName,
            @RequestParam String token) {
        RecordingService.PlaybackAsset asset = service.playbackAsset(recordingId, objectName, token);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(asset.contentType()))
                .body(asset.bytes());
    }
}
