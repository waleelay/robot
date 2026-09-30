package com.robot.control.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;

import com.robot.control.auth.CurrentUserResolver;
import com.robot.control.call.IntercomCallService;
import com.robot.control.dto.ControlStartVideoRequest;
import com.robot.control.service.EquipmentControlService;
import com.robot.control.service.ControlVideoCommandService;
import com.robot.control.service.MultiFunctionAudioTransferService;
import com.robot.media.common.video.VideoSessionResponse;
import com.robot.media.common.video.IntercomResponse;
import com.robot.control.robot.service.RobotRegistryService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 面向前端的机器人控制接口入口。
 *
 * @author leelay
 * @date 2026-07-05
 */
@RestController
@RequestMapping("/api/control/robots")
public class ControlRobotController {

    private final ControlVideoCommandService controlVideoCommandService;
    private final EquipmentControlService equipmentControlService;
    private final CurrentUserResolver currentUserResolver;
    private final IntercomCallService intercomCallService;
    private final MultiFunctionAudioTransferService multiFunctionAudioTransferService;
    private final RobotRegistryService robotRegistryService;

    /**
     * 创建 ControlRobotController 实例。
     * @param controlVideoCommandService 视频控制编排服务
     * @param equipmentControlService 装备控制服务
     * @param currentUserResolver 当前用户解析器
     *
     * @param intercomCallService 在媒体对讲启动前协调机器人主动呼叫的邀请、接听与状态流转。
     * @param multiFunctionAudioTransferService 多合一设备音频文件下发服务。
     * @param robotRegistryService 维护机器人运行状态与在线事实的注册服务
     */
    public ControlRobotController(
            ControlVideoCommandService controlVideoCommandService,
            EquipmentControlService equipmentControlService,
            CurrentUserResolver currentUserResolver,
            IntercomCallService intercomCallService,
            MultiFunctionAudioTransferService multiFunctionAudioTransferService,
            RobotRegistryService robotRegistryService) {
        this.controlVideoCommandService = controlVideoCommandService;
        this.equipmentControlService = equipmentControlService;
        this.currentUserResolver = currentUserResolver;
        this.intercomCallService = intercomCallService;
        this.multiFunctionAudioTransferService = multiFunctionAudioTransferService;
        this.robotRegistryService = robotRegistryService;
    }

    /**
     * 查询 控制服务 当前内存中的机器人注册状态。
     *
     * @return 机器人注册表快照
     */
    @Operation(
            operationId = "controlRobotController_registry",
            summary = "查询当前内存机器人注册状态",
            description = "查询当前内存机器人注册状态。受控服务入口；使用用户上下文的方法依赖可信上游 Header，编排成功不等于设备已完成动作。",
            tags = {"ControlRobotController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @GetMapping("/registry")
    public Map<String, Object> registry() {
        List<?> records = robotRegistryService.list();
        return Map.of(
                "records", records,
                "total", records.size());
    }

    /**
     * 查询机器人控制画像。
     *
     * @param robotId 机器人 ID
     * @return 机器人控制画像
     */
    @Operation(
            operationId = "controlRobotController_controlProfile",
            summary = "查询机器人设备能力与控制画像",
            description = "查询机器人设备能力与控制画像。受控服务入口；使用用户上下文的方法依赖可信上游 Header，编排成功不等于设备已完成动作。",
            tags = {"ControlRobotController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @GetMapping("/{robotId}/control-profile")
    public Map<String, Object> controlProfile(@PathVariable String robotId) {
        return equipmentControlService.controlProfile(robotId);
    }

    /**
     * 申请机器人控制权并返回控制会话。
     *
     * @param robotId 机器人 ID
     * @param request 请求参数
     * @param servletRequest HTTP 请求
     * @return 控制会话信息
     */
    @Operation(
            operationId = "controlRobotController_acquireControl",
            summary = "获取指定范围的机器人控制权",
            description = "获取指定范围的机器人控制权。受控服务入口；使用用户上下文的方法依赖可信上游 Header，编排成功不等于设备已完成动作。",
            tags = {"ControlRobotController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @PostMapping("/{robotId}/control-sessions/acquire")
    public Map<String, Object> acquireControl(
            @PathVariable String robotId,
            @RequestBody Map<String, Object> request,
            HttpServletRequest servletRequest) {
        return equipmentControlService.acquire(robotId, request, currentUserResolver.resolve(servletRequest));
    }

    /**
     * 从导航模式发起人工接管。
     *
     * @param robotId 机器人 ID
     * @param request 请求参数
     * @param servletRequest HTTP 请求
     * @return 本体控制会话和模式切换发布结果
     */
    @Operation(
            operationId = "controlRobotController_takeoverControl",
            summary = "从导航模式发起人工接管",
            description = "从导航模式发起人工接管。受控服务入口；使用用户上下文的方法依赖可信上游 Header，编排成功不等于设备已完成动作。",
            tags = {"ControlRobotController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @PostMapping("/{robotId}/control-sessions/takeover")
    public Map<String, Object> takeoverControl(
            @PathVariable String robotId,
            @RequestBody Map<String, Object> request,
            HttpServletRequest servletRequest) {
        return equipmentControlService.takeover(robotId, request, currentUserResolver.resolve(servletRequest));
    }

    /**
     * 设置机器人控制模式。
     *
     * @param robotId 机器人 ID
     * @param request 请求参数
     * @param servletRequest HTTP 请求
     * @return 控制模式设置结果
     */
    @Operation(
            operationId = "controlRobotController_setControlMode",
            summary = "请求切换机器人控制模式",
            description = "请求切换机器人控制模式。受控服务入口；使用用户上下文的方法依赖可信上游 Header，编排成功不等于设备已完成动作。",
            tags = {"ControlRobotController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @PostMapping("/{robotId}/control-mode")
    public Map<String, Object> setControlMode(
            @PathVariable String robotId,
            @RequestBody Map<String, Object> request,
            HttpServletRequest servletRequest) {
        return equipmentControlService.setControlMode(robotId, request, currentUserResolver.resolve(servletRequest));
    }

    /**
     * 释放指定控制会话。
     * @param robotId 机器人 ID
     * @param controlSessionId 控制会话 ID
     * @param request 请求参数
     * @return 控制会话释放结果
     *
     * @param servletRequest HTTP 请求
     */
    @Operation(
            operationId = "controlRobotController_releaseControl",
            summary = "释放当前用户拥有的指定控制会话",
            description = "释放当前用户拥有的指定控制会话。受控服务入口；使用用户上下文的方法依赖可信上游 Header，编排成功不等于设备已完成动作。",
            tags = {"ControlRobotController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @PostMapping("/{robotId}/control-sessions/{controlSessionId}/release")
    public Map<String, Object> releaseControl(
            @PathVariable String robotId,
            @PathVariable String controlSessionId,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest servletRequest) {
        return equipmentControlService.release(
                robotId,
                controlSessionId,
                request,
                currentUserResolver.resolve(servletRequest));
    }

    /**
     * 生成高风险控制确认 Token。
     *
     * @param robotId 机器人 ID
     * @param request 请求参数
     * @param servletRequest HTTP 请求
     * @return 确认 Token 信息
     */
    @Operation(
            operationId = "controlRobotController_confirmToken",
            summary = "为高风险动作签发短期确认令牌",
            description = "为高风险动作签发短期确认令牌。受控服务入口；使用用户上下文的方法依赖可信上游 Header，编排成功不等于设备已完成动作。",
            tags = {"ControlRobotController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @PostMapping("/{robotId}/commands/confirm-token")
    public Map<String, Object> confirmToken(
            @PathVariable String robotId,
            @RequestBody Map<String, Object> request,
            HttpServletRequest servletRequest) {
        return equipmentControlService.confirmToken(robotId, request, currentUserResolver.resolve(servletRequest));
    }

    /**
     * 发布装备控制命令。
     *
     * @param robotId 机器人 ID
     * @param request 请求参数
     * @param servletRequest HTTP 请求
     * @return 命令发布结果
     */
    @Operation(
            operationId = "controlRobotController_command",
            summary = "校验控制权和动作参数后发布设备控制命令",
            description = "校验控制权和动作参数后发布设备控制命令。受控服务入口；使用用户上下文的方法依赖可信上游 Header，编排成功不等于设备已完成动作。",
            tags = {"ControlRobotController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @PostMapping("/{robotId}/commands")
    public Map<String, Object> command(
            @PathVariable String robotId,
            @RequestBody Map<String, Object> request,
            HttpServletRequest servletRequest) {
        return equipmentControlService.publishCommand(robotId, request, currentUserResolver.resolve(servletRequest));
    }

    /**
     * 将 媒体服务 中已就绪的音频文件下发给目标机器人客户端。
     *
     * @param robotId 机器人 ID
     * @param deviceId 多合一设备 ID
     * @param request 包含 fileId 的请求参数
     * @param servletRequest HTTP 请求
     * @return 文件中转任务发布结果
     */
    @Operation(
            operationId = "controlRobotController_transferMultiFunctionAudioFile",
            summary = "将已就绪音频文件下发给目标设备客户端",
            description = "将已就绪音频文件下发给目标设备客户端。受控服务入口；使用用户上下文的方法依赖可信上游 Header，编排成功不等于设备已完成动作。",
            tags = {"ControlRobotController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @PostMapping("/{robotId}/devices/{deviceId}/audio-file-transfers")
    public Map<String, Object> transferMultiFunctionAudioFile(
            @PathVariable String robotId,
            @PathVariable String deviceId,
            @RequestBody Map<String, Object> request,
            HttpServletRequest servletRequest) {
        return multiFunctionAudioTransferService.transfer(
                robotId,
                deviceId,
                request,
                currentUserResolver.resolve(servletRequest));
    }

    /**
     * 编排启动视频流程。
     *
     * @param robotId 机器人 ID
     * @param deviceId 设备 ID
     * @param request 请求参数
     * @param servletRequest HTTP 请求
     * @return 视频会话响应
     */
    @Operation(
            operationId = "controlRobotController_startVideo",
            summary = "编排启动机器人摄像头视频",
            description = "编排启动机器人摄像头视频。受控服务入口；使用用户上下文的方法依赖可信上游 Header，编排成功不等于设备已完成动作。",
            tags = {"ControlRobotController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @PostMapping("/{robotId}/cameras/{deviceId}/video/start")
    public VideoSessionResponse startVideo(
            @PathVariable String robotId,
            @PathVariable String deviceId,
            @RequestBody(required = false) ControlStartVideoRequest request,
            HttpServletRequest servletRequest) {
        return controlVideoCommandService.startVideo(robotId, deviceId, request, currentUserResolver.resolve(servletRequest));
    }

    /**
     * 启动对讲。
     *
     * @param robotId 机器人 ID
     * @param deviceId 设备 ID
     * @param request 请求参数
     * @param servletRequest HTTP 请求
     * @return 对讲会话响应
     */
    @Operation(
            operationId = "controlRobotController_startIntercom",
            summary = "编排启动机器人摄像头对讲",
            description = "编排启动机器人摄像头对讲。受控服务入口；使用用户上下文的方法依赖可信上游 Header，编排成功不等于设备已完成动作。",
            tags = {"ControlRobotController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @PostMapping("/{robotId}/cameras/{deviceId}/video/intercom/start")
    public IntercomResponse startIntercom(
            @PathVariable String robotId,
            @PathVariable String deviceId,
            @RequestBody(required = false) ControlStartVideoRequest request,
            HttpServletRequest servletRequest) {
        intercomCallService.requireManualIntercomAllowed(robotId);
        return controlVideoCommandService.startIntercom(robotId, deviceId, request, currentUserResolver.resolve(servletRequest));
    }
}
