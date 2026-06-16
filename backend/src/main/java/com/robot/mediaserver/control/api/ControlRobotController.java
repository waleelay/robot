package com.robot.mediaserver.control.api;

import com.robot.mediaserver.auth.CurrentUserResolver;
import com.robot.mediaserver.control.client.ControlMediaServiceClient;
import com.robot.mediaserver.control.dto.ControlStartVideoRequest;
import com.robot.mediaserver.control.service.EquipmentControlService;
import com.robot.mediaserver.control.service.ControlVideoCommandService;
import com.robot.mediaserver.video.dto.VideoSessionResponse;
import com.robot.mediaserver.video.dto.IntercomResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/control/robots")
public class ControlRobotController {

    private final ControlVideoCommandService controlVideoCommandService;
    private final EquipmentControlService equipmentControlService;
    private final CurrentUserResolver currentUserResolver;

    public ControlRobotController(
            ControlVideoCommandService controlVideoCommandService,
            EquipmentControlService equipmentControlService,
            CurrentUserResolver currentUserResolver) {
        this.controlVideoCommandService = controlVideoCommandService;
        this.equipmentControlService = equipmentControlService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return equipmentControlService.robots();
    }

    @GetMapping("/{robotId}/control-profile")
    public Map<String, Object> controlProfile(@PathVariable String robotId) {
        return equipmentControlService.controlProfile(robotId);
    }

    @PostMapping("/{robotId}/control-sessions/acquire")
    public Map<String, Object> acquireControl(
            @PathVariable String robotId,
            @RequestBody Map<String, Object> request,
            HttpServletRequest servletRequest) {
        return equipmentControlService.acquire(robotId, request, currentUserResolver.resolve(servletRequest));
    }

    @PostMapping("/{robotId}/control-sessions/takeover")
    public Map<String, Object> takeoverControl(
            @PathVariable String robotId,
            @RequestBody Map<String, Object> request,
            HttpServletRequest servletRequest) {
        return equipmentControlService.takeover(robotId, request, currentUserResolver.resolve(servletRequest));
    }

    @PostMapping("/{robotId}/control-sessions/{controlSessionId}/heartbeat")
    public Map<String, Object> heartbeatControl(
            @PathVariable String robotId,
            @PathVariable String controlSessionId) {
        return equipmentControlService.heartbeat(robotId, controlSessionId);
    }

    @PostMapping("/{robotId}/control-sessions/{controlSessionId}/release")
    public Map<String, Object> releaseControl(
            @PathVariable String robotId,
            @PathVariable String controlSessionId,
            @RequestBody(required = false) Map<String, Object> request) {
        return equipmentControlService.release(robotId, controlSessionId, request);
    }

    @GetMapping("/{robotId}/control-sessions/active")
    public List<Map<String, Object>> activeControlSessions(@PathVariable String robotId) {
        return equipmentControlService.activeSessions(robotId);
    }

    @PostMapping("/{robotId}/commands/confirm-token")
    public Map<String, Object> confirmToken(
            @PathVariable String robotId,
            @RequestBody Map<String, Object> request,
            HttpServletRequest servletRequest) {
        return equipmentControlService.confirmToken(robotId, request, currentUserResolver.resolve(servletRequest));
    }

    @PostMapping("/{robotId}/commands")
    public Map<String, Object> command(
            @PathVariable String robotId,
            @RequestBody Map<String, Object> request,
            HttpServletRequest servletRequest) {
        return equipmentControlService.publishCommand(robotId, request, currentUserResolver.resolve(servletRequest));
    }

    @PostMapping("/{robotId}/cameras/{deviceId}/video/start")
    public VideoSessionResponse startVideo(
            @PathVariable String robotId,
            @PathVariable String deviceId,
            @RequestBody(required = false) ControlStartVideoRequest request,
            HttpServletRequest servletRequest) {
        return controlVideoCommandService.startVideo(robotId, deviceId, request, currentUserResolver.resolve(servletRequest));
    }

    @PostMapping("/{robotId}/cameras/{deviceId}/video/intercom/start")
    public IntercomResponse startIntercom(
            @PathVariable String robotId,
            @PathVariable String deviceId,
            @RequestBody(required = false) ControlStartVideoRequest request,
            HttpServletRequest servletRequest) {
        return controlVideoCommandService.startIntercom(robotId, deviceId, request, currentUserResolver.resolve(servletRequest));
    }
}
