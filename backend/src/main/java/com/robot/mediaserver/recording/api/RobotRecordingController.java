package com.robot.mediaserver.recording.api;

import com.robot.mediaserver.recording.dto.CreateRecordingUploadRequest;
import com.robot.mediaserver.recording.dto.PartUrlsRequest;
import com.robot.mediaserver.recording.dto.PartUrlsResponse;
import com.robot.mediaserver.recording.dto.RecordingStatusResponse;
import com.robot.mediaserver.recording.dto.RecordingUploadResponse;
import com.robot.mediaserver.recording.service.RecordingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/media")
public class RobotRecordingController {

    private final RecordingService service;

    public RobotRecordingController(RecordingService service) {
        this.service = service;
    }

    @PostMapping("/recording-uploads")
    public RecordingUploadResponse createOrResume(
            @RequestHeader("X-Robot-Id") String robotId,
            @Valid @RequestBody CreateRecordingUploadRequest request) {
        return service.createOrResume(robotId, request);
    }

    @PostMapping("/recording-uploads/{uploadId}/part-urls")
    public PartUrlsResponse partUrls(
            @RequestHeader("X-Robot-Id") String robotId,
            @PathVariable String uploadId,
            @Valid @RequestBody PartUrlsRequest request) {
        return service.partUrls(robotId, uploadId, request.getPartNumbers());
    }

    @PostMapping("/recording-uploads/{uploadId}/complete")
    public RecordingStatusResponse complete(
            @RequestHeader("X-Robot-Id") String robotId,
            @PathVariable String uploadId) {
        return service.complete(robotId, uploadId);
    }

    @GetMapping("/recordings/{recordingId}/status")
    public RecordingStatusResponse status(
            @RequestHeader("X-Robot-Id") String robotId,
            @PathVariable String recordingId) {
        return service.robotStatus(robotId, recordingId);
    }
}
