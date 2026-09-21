package com.robot.control.api;

import com.robot.control.fixedcamera.FixedCameraPublisherLifecycleService;
import com.robot.media.common.video.FixedCameraPublisherModeResponse;
import com.robot.media.common.video.VideoPublisherMode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Management 调用的固定摄像头发布端生命周期入口。 */
@RestController
@RequestMapping("/internal/control/fixed-cameras/{cameraId}")
public class InternalFixedCameraPublisherController {

    private final FixedCameraPublisherLifecycleService service;

    @Value("${control.fixed-camera-publisher.trusted-caller:management-service}")
    private String trustedCaller = "management-service";

    public InternalFixedCameraPublisherController(FixedCameraPublisherLifecycleService service) {
        this.service = service;
    }

    @PostMapping("/publisher-mode-switch")
    public FixedCameraPublisherModeResponse switchMode(
            @PathVariable String cameraId,
            @Valid @RequestBody ModeSwitchRequest request,
            HttpServletRequest servletRequest) {
        requireTrustedCaller(servletRequest);
        return service.switchMode(cameraId, request.targetMode(), request.publisherRevision());
    }

    @PostMapping("/publisher-quiesce")
    public FixedCameraPublisherModeResponse quiesce(
            @PathVariable String cameraId,
            @Valid @RequestBody QuiesceRequest request,
            HttpServletRequest servletRequest) {
        requireTrustedCaller(servletRequest);
        if (!"DELETE".equals(request.reason())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "固定摄像头收口原因不合法");
        }
        return service.quiesceForDelete(cameraId, request.publisherRevision());
    }

    private void requireTrustedCaller(HttpServletRequest request) {
        if (!trustedCaller.equals(request.getHeader("X-Internal-Caller"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "固定摄像头发布编排调用方不受信任");
        }
    }

    public record ModeSwitchRequest(@NotNull VideoPublisherMode targetMode, long publisherRevision) {
    }

    public record QuiesceRequest(long publisherRevision, @NotNull String reason) {
    }
}
