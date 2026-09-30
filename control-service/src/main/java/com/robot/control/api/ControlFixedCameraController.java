package com.robot.control.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;

import com.robot.control.auth.CurrentUserResolver;
import com.robot.control.dto.ControlStartVideoRequest;
import com.robot.control.dto.FixedCameraBatchStartRequest;
import com.robot.media.common.video.VideoSessionResponse;
import com.robot.control.service.ControlVideoCommandService;
import com.robot.control.client.ControlManagementClient;
import com.robot.control.config.ControlServiceProperties;
import com.robot.control.fixedcamera.FixedCameraHealthService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
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
    private final ControlManagementClient managementClient;
    private final FixedCameraHealthService healthService;
    private final ControlServiceProperties properties;

    /**
     * 初始化 ControlFixedCameraController，保存所需依赖及初始运行状态。
     *
     * @param controlVideoCommandService 视频控制编排服务
     * @param currentUserResolver 当前用户解析器
     * @param managementClient 访问 Management 档案与权限接口的客户端
     * @param healthService 保存固定摄像头 Gateway 与 RTSP 最近健康状态。
     * @param properties 服务配置
     */
    public ControlFixedCameraController(
            ControlVideoCommandService controlVideoCommandService,
            CurrentUserResolver currentUserResolver,
            ControlManagementClient managementClient,
            FixedCameraHealthService healthService,
            ControlServiceProperties properties) {
        this.controlVideoCommandService = controlVideoCommandService;
        this.currentUserResolver = currentUserResolver;
        this.managementClient = managementClient;
        this.healthService = healthService;
        this.properties = properties;
    }

    /**
     * 返回当前用户有权固定摄像头的最新健康状态。
     *
     * @return 当前用户授权范围内的摄像头健康快照
     */
    @Operation(
            operationId = "controlFixedCameraController_health",
            summary = "查询当前授权固定摄像头的健康状态",
            description = "查询当前授权固定摄像头的健康状态。受控服务入口；使用用户上下文的方法依赖可信上游 Header，编排成功不等于设备已完成动作。",
            tags = {"ControlFixedCameraController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @GetMapping("/health")
    public Map<String, Object> health() {
        return healthService.authorizedSnapshot(
                managementClient.fixedCameras(), properties.getMqtt().getFixedCameraGatewayId());
    }

    /**
     * 启动单路固定摄像头实时视频。
     *
     * @param cameraId 固定摄像头 ID
     * @param request 请求参数
     * @param servletRequest HTTP 请求
     * @return 视频会话响应
     */
    @Operation(
            operationId = "controlFixedCameraController_startVideo",
            summary = "编排启动单路固定摄像头视频",
            description = "编排启动单路固定摄像头视频。受控服务入口；使用用户上下文的方法依赖可信上游 Header，编排成功不等于设备已完成动作。",
            tags = {"ControlFixedCameraController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
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
    @Operation(
            operationId = "controlFixedCameraController_startVideos",
            summary = "分别编排多路固定摄像头视频并汇总逐项结果",
            description = "分别编排多路固定摄像头视频并汇总逐项结果。受控服务入口；使用用户上下文的方法依赖可信上游 Header，编排成功不等于设备已完成动作。",
            tags = {"ControlFixedCameraController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
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
