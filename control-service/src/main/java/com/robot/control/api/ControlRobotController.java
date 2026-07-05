package com.robot.control.api;

import com.robot.control.auth.CurrentUserResolver;
import com.robot.control.client.ControlMediaServiceClient;
import com.robot.control.dto.ControlStartVideoRequest;
import com.robot.control.service.EquipmentControlService;
import com.robot.control.service.ControlVideoCommandService;
import com.robot.control.dto.VideoSessionResponse;
import com.robot.control.dto.IntercomResponse;
import jakarta.servlet.http.HttpServletRequest;
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

    /**
     * 创建 ControlRobotController 实例。
     *
     * @param controlVideoCommandService 视频控制编排服务
     * @param equipmentControlService 装备控制服务
     * @param currentUserResolver 当前用户解析器
     */
    public ControlRobotController(
            ControlVideoCommandService controlVideoCommandService,
            EquipmentControlService equipmentControlService,
            CurrentUserResolver currentUserResolver) {
        this.controlVideoCommandService = controlVideoCommandService;
        this.equipmentControlService = equipmentControlService;
        this.currentUserResolver = currentUserResolver;
    }

    /**
     * 查询机器人控制画像。
     *
     * @param robotId 机器人 ID
     * @return 机器人控制画像
     */
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
    @PostMapping("/{robotId}/control-sessions/acquire")
    public Map<String, Object> acquireControl(
            @PathVariable String robotId,
            @RequestBody Map<String, Object> request,
            HttpServletRequest servletRequest) {
        return equipmentControlService.acquire(robotId, request, currentUserResolver.resolve(servletRequest));
    }

    /**
     * 强制接管机器人控制权。
     *
     * @param robotId 机器人 ID
     * @param request 请求参数
     * @param servletRequest HTTP 请求
     * @return 接管后的控制会话信息
     */
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
    @PostMapping("/{robotId}/control-mode")
    public Map<String, Object> setControlMode(
            @PathVariable String robotId,
            @RequestBody Map<String, Object> request,
            HttpServletRequest servletRequest) {
        return equipmentControlService.setControlMode(robotId, request, currentUserResolver.resolve(servletRequest));
    }

    /**
     * 释放指定控制会话。
     *
     * @param robotId 机器人 ID
     * @param controlSessionId 控制会话 ID
     * @param request 请求参数
     * @return 控制会话释放结果
     */
    @PostMapping("/{robotId}/control-sessions/{controlSessionId}/release")
    public Map<String, Object> releaseControl(
            @PathVariable String robotId,
            @PathVariable String controlSessionId,
            @RequestBody(required = false) Map<String, Object> request) {
        return equipmentControlService.release(robotId, controlSessionId, request);
    }

    /**
     * 生成高风险控制确认 Token。
     *
     * @param robotId 机器人 ID
     * @param request 请求参数
     * @param servletRequest HTTP 请求
     * @return 确认 Token 信息
     */
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
    @PostMapping("/{robotId}/commands")
    public Map<String, Object> command(
            @PathVariable String robotId,
            @RequestBody Map<String, Object> request,
            HttpServletRequest servletRequest) {
        return equipmentControlService.publishCommand(robotId, request, currentUserResolver.resolve(servletRequest));
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
    @PostMapping("/{robotId}/cameras/{deviceId}/video/intercom/start")
    public IntercomResponse startIntercom(
            @PathVariable String robotId,
            @PathVariable String deviceId,
            @RequestBody(required = false) ControlStartVideoRequest request,
            HttpServletRequest servletRequest) {
        return controlVideoCommandService.startIntercom(robotId, deviceId, request, currentUserResolver.resolve(servletRequest));
    }
}
