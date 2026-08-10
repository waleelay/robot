package com.robot.control.api;

import com.robot.control.auth.CurrentUserResolver;
import com.robot.control.dto.ControlStartVideoRequest;
import com.robot.control.dto.FixedCameraBatchStartRequest;
import com.robot.control.dto.VideoSessionResponse;
import com.robot.control.service.ControlVideoCommandService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 面向前端的固定摄像头视频接口入口。
 *
 * @author leelay
 * @date 2026-08-10
 */
@RestController
@RequestMapping("/api/control/fixed-cameras")
public class ControlFixedCameraController {

    private final ControlVideoCommandService controlVideoCommandService;
    private final CurrentUserResolver currentUserResolver;

    public ControlFixedCameraController(
            ControlVideoCommandService controlVideoCommandService,
            CurrentUserResolver currentUserResolver) {
        this.controlVideoCommandService = controlVideoCommandService;
        this.currentUserResolver = currentUserResolver;
    }

    /**
     * 启动单路固定摄像头实时视频。
     *
     * @param cameraId 固定摄像头 ID
     * @param request 请求参数
     * @param servletRequest HTTP 请求
     * @return 视频会话响应
     */
    @PostMapping("/{cameraId}/video/start")
    public VideoSessionResponse startVideo(
            @PathVariable String cameraId,
            @RequestBody(required = false) ControlStartVideoRequest request,
            HttpServletRequest servletRequest) {
        return controlVideoCommandService.startFixedCameraVideo(
                cameraId,
                request,
                currentUserResolver.resolve(servletRequest));
    }

    /**
     * 批量启动固定摄像头实时视频。
     *
     * @param request 请求参数
     * @param servletRequest HTTP 请求
     * @return 批量启动结果
     */
    @PostMapping("/video/start")
    public Map<String, Object> startVideos(
            @RequestBody FixedCameraBatchStartRequest request,
            HttpServletRequest servletRequest) {
        FixedCameraBatchStartRequest body = request == null ? new FixedCameraBatchStartRequest() : request;
        return controlVideoCommandService.startFixedCameraVideos(
                body.getCameraIds(),
                body,
                currentUserResolver.resolve(servletRequest));
    }
}
