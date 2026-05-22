package com.robot.mediaserver.control.api;

import com.robot.mediaserver.auth.CurrentUserResolver;
import com.robot.mediaserver.control.dto.ControlStartVideoRequest;
import com.robot.mediaserver.robot.dto.RobotDeviceResponse;
import com.robot.mediaserver.robot.service.RobotRegistryService;
import com.robot.mediaserver.video.dto.CreateVideoSessionRequest;
import com.robot.mediaserver.video.dto.VideoSessionResponse;
import com.robot.mediaserver.video.model.VideoChannel;
import com.robot.mediaserver.video.model.VideoQuality;
import com.robot.mediaserver.video.service.VideoSessionService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/control/robots")
public class ControlRobotController {

    private final RobotRegistryService registryService;
    private final VideoSessionService videoSessionService;
    private final CurrentUserResolver currentUserResolver;

    public ControlRobotController(
            RobotRegistryService registryService,
            VideoSessionService videoSessionService,
            CurrentUserResolver currentUserResolver) {
        this.registryService = registryService;
        this.videoSessionService = videoSessionService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping
    public List<RobotDeviceResponse> list() {
        return registryService.list();
    }

    @PostMapping("/{robotId}/cameras/{deviceId}/video/start")
    public VideoSessionResponse startVideo(
            @PathVariable String robotId,
            @PathVariable String deviceId,
            @RequestBody(required = false) ControlStartVideoRequest request,
            HttpServletRequest servletRequest) {
        ControlStartVideoRequest startRequest = request == null ? new ControlStartVideoRequest() : request;
        CreateVideoSessionRequest mediaRequest = new CreateVideoSessionRequest();
        mediaRequest.setRobotId(robotId);
        mediaRequest.setDeviceId(deviceId);
        mediaRequest.setChannel(startRequest.getChannel() == null ? VideoChannel.visible : startRequest.getChannel());
        mediaRequest.setQuality(startRequest.getQuality() == null ? VideoQuality.sub : startRequest.getQuality());
        mediaRequest.setReuse(startRequest.isReuse());
        mediaRequest.setClientRequestId(startRequest.getClientRequestId());
        return videoSessionService.create(mediaRequest, currentUserResolver.resolve(servletRequest));
    }
}
