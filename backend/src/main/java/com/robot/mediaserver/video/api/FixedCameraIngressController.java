package com.robot.mediaserver.video.api;

import com.robot.media.common.video.FixedCameraIngressResponse;
import com.robot.media.common.video.FixedCameraIngressStatusRequest;
import com.robot.mediaserver.video.service.FixedCameraIngressService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Media 内部固定摄像头 Ingress 管理接口。 */
@RestController
@RequestMapping("/internal/media/fixed-camera-ingresses")
public class FixedCameraIngressController {

    private static final String REVISION_HEADER = "X-Ingress-Operation-Revision";

    private final FixedCameraIngressService service;

    public FixedCameraIngressController(FixedCameraIngressService service) {
        this.service = service;
    }

    @PostMapping
    public FixedCameraIngressResponse create(
            @Valid @RequestBody CameraRequest request,
            @RequestHeader(REVISION_HEADER) long revision) {
        return service.create(request.cameraId(), revision);
    }

    @GetMapping("/{cameraId}")
    public FixedCameraIngressResponse get(@PathVariable String cameraId) {
        return service.get(cameraId);
    }

    @PutMapping("/{cameraId}")
    public FixedCameraIngressResponse rotate(
            @PathVariable String cameraId,
            @RequestHeader(REVISION_HEADER) long revision) {
        return service.rotate(cameraId, revision);
    }

    @DeleteMapping("/{cameraId}")
    public void revoke(
            @PathVariable String cameraId,
            @RequestHeader(REVISION_HEADER) long revision) {
        service.revoke(cameraId, revision);
    }

    @PostMapping("/status-query")
    public List<FixedCameraIngressResponse> statuses(@RequestBody FixedCameraIngressStatusRequest request) {
        return service.statuses(request.cameraIds());
    }

    public record CameraRequest(@NotBlank String cameraId) {
    }
}
