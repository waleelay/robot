package com.robot.mediaserver.control.api;

import com.robot.mediaserver.auth.CurrentUserResolver;
import com.robot.mediaserver.control.client.ControlMediaServiceClient;
import com.robot.mediaserver.recording.dto.PlaybackUrlResponse;
import com.robot.mediaserver.recording.dto.RecordingListResponse;
import com.robot.mediaserver.recording.model.RecordingStatus;
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
@RequestMapping("/api/control/recordings")
public class ControlRecordingController {

    private final ControlMediaServiceClient mediaServiceClient;
    private final CurrentUserResolver currentUserResolver;

    public ControlRecordingController(
            ControlMediaServiceClient mediaServiceClient,
            CurrentUserResolver currentUserResolver) {
        this.mediaServiceClient = mediaServiceClient;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping
    public RecordingListResponse list(
            @RequestParam(required = false) String robotId,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) RecordingStatus status,
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        return mediaServiceClient.recordings(
                robotId,
                deviceId,
                sourceType,
                status,
                from,
                to,
                page,
                size,
                currentUserResolver.resolve(request));
    }

    @PostMapping("/{recordingId}/play-url")
    public PlaybackUrlResponse playUrl(@PathVariable String recordingId, HttpServletRequest request) {
        return mediaServiceClient.recordingPlayUrl(recordingId, currentUserResolver.resolve(request));
    }

    @GetMapping("/{recordingId}/hls/{objectName}")
    public ResponseEntity<byte[]> hls(
            @PathVariable String recordingId,
            @PathVariable String objectName,
            @RequestParam String token) {
        byte[] body = mediaServiceClient.recordingHlsAsset(recordingId, objectName, token);
        MediaType contentType = MediaType.parseMediaType(hlsContentType(objectName));
        return ResponseEntity.ok().contentType(contentType).body(body);
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
