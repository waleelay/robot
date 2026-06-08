package com.robot.mediaserver.video.internal;

import com.robot.mediaserver.video.dto.CompleteSnapshotRequest;
import com.robot.mediaserver.video.dto.FailSnapshotRequest;
import com.robot.mediaserver.video.dto.SnapshotResponse;
import com.robot.mediaserver.video.service.SnapshotService;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping({"/api/internal/media/snapshots", "/internal/media/snapshots"})
public class SnapshotWorkerController {

    private final SnapshotService snapshotService;

    public SnapshotWorkerController(SnapshotService snapshotService) {
        this.snapshotService = snapshotService;
    }

    @PostMapping("/{snapshotId}/complete")
    public SnapshotResponse complete(
            @PathVariable String snapshotId,
            @RequestBody CompleteSnapshotRequest request) {
        return snapshotService.complete(snapshotId, request, null);
    }

    @PostMapping(value = "/{snapshotId}/complete-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SnapshotResponse completeFile(
            @PathVariable String snapshotId,
            @ModelAttribute CompleteSnapshotRequest request,
            @RequestPart("file") MultipartFile file) {
        return snapshotService.complete(snapshotId, request, file);
    }

    @PostMapping("/{snapshotId}/fail")
    public SnapshotResponse fail(
            @PathVariable String snapshotId,
            @RequestBody FailSnapshotRequest request) {
        return snapshotService.fail(snapshotId, request);
    }

    @GetMapping(value = "/{snapshotId}/image", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> image(@PathVariable String snapshotId) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .body(snapshotService.image(snapshotId));
    }
}
