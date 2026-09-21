package com.robot.mediaserver.video.api;

import com.robot.media.common.video.FixedCameraPublisherModeRequest;
import com.robot.media.common.video.FixedCameraPublisherModeResponse;
import com.robot.media.common.video.FixedCameraPublisherPresenceResponse;
import com.robot.mediaserver.video.service.VideoSessionService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Control 专用的固定摄像头发布端生命周期接口。 */
@RestController
@RequestMapping("/internal/media/fixed-camera-sources/{cameraId}")
public class FixedCameraSourceController {

    private static final String REVISION_HEADER = "X-Publisher-Revision";

    private final VideoSessionService service;

    @Value("${media.fixed-camera-publisher.trusted-caller:control-service}")
    private String trustedCaller = "control-service";

    public FixedCameraSourceController(VideoSessionService service) {
        this.service = service;
    }

    @PostMapping("/publisher-mode")
    public FixedCameraPublisherModeResponse switchPublisherMode(
            @PathVariable String cameraId,
            @RequestHeader(REVISION_HEADER) long publisherRevision,
            @Valid @RequestBody FixedCameraPublisherModeRequest request,
            HttpServletRequest servletRequest) {
        requireTrustedCaller(servletRequest);
        return service.switchFixedCameraPublisherMode(cameraId, request.targetMode(), publisherRevision);
    }

    @PostMapping("/quiesce")
    public FixedCameraPublisherModeResponse quiesce(
            @PathVariable String cameraId,
            @RequestHeader(REVISION_HEADER) long publisherRevision,
            HttpServletRequest servletRequest) {
        requireTrustedCaller(servletRequest);
        return service.quiesceFixedCameraPublisher(cameraId, publisherRevision);
    }

    @GetMapping("/publisher-presence")
    public FixedCameraPublisherPresenceResponse publisherPresence(
            @PathVariable String cameraId,
            HttpServletRequest servletRequest) {
        requireTrustedCaller(servletRequest);
        return service.fixedCameraPublisherPresence(cameraId);
    }

    private void requireTrustedCaller(HttpServletRequest request) {
        if (!trustedCaller.equals(request.getHeader("X-Internal-Caller"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "固定摄像头发布端调用方不受信任");
        }
    }
}
