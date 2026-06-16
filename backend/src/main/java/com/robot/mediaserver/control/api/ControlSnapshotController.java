package com.robot.mediaserver.control.api;

import com.robot.mediaserver.control.client.ControlMediaServiceClient;
import com.robot.mediaserver.video.dto.SnapshotListResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/control/snapshots")
public class ControlSnapshotController {

    private final ControlMediaServiceClient mediaServiceClient;

    public ControlSnapshotController(ControlMediaServiceClient mediaServiceClient) {
        this.mediaServiceClient = mediaServiceClient;
    }

    @GetMapping
    public SnapshotListResponse list(
            @RequestParam(required = false) String robotId,
            @RequestParam(required = false) String deviceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return mediaServiceClient.snapshots(robotId, deviceId, page, pageSize);
    }

    @GetMapping(value = "/{snapshotId}/image", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> image(@PathVariable String snapshotId) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .body(mediaServiceClient.snapshotImage(snapshotId));
    }
}
