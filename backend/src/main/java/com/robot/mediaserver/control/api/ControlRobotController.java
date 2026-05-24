package com.robot.mediaserver.control.api;

import com.robot.mediaserver.auth.CurrentUserResolver;
import com.robot.mediaserver.control.dto.ControlStartVideoRequest;
import com.robot.mediaserver.control.service.ControlVideoCommandService;
import com.robot.mediaserver.robot.dto.RobotDeviceResponse;
import com.robot.mediaserver.robot.service.RobotRegistryService;
import com.robot.mediaserver.video.dto.VideoSessionResponse;
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
    private final ControlVideoCommandService controlVideoCommandService;
    private final CurrentUserResolver currentUserResolver;

    public ControlRobotController(
            RobotRegistryService registryService,
            ControlVideoCommandService controlVideoCommandService,
            CurrentUserResolver currentUserResolver) {
        this.registryService = registryService;
        this.controlVideoCommandService = controlVideoCommandService;
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
        return controlVideoCommandService.startVideo(robotId, deviceId, request, currentUserResolver.resolve(servletRequest));
    }
}
