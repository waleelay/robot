package com.robot.control.api;

import com.robot.control.auth.CurrentUser;
import com.robot.control.auth.CurrentUserResolver;
import com.robot.control.call.IntercomCallService;
import com.robot.control.client.ControlMediaServiceClient;
import com.robot.control.service.ControlVideoCommandService;
import com.robot.media.common.file.FileListItemResponse;
import com.robot.media.common.video.MediaTrackResponse;
import com.robot.media.common.video.SwitchChannelRequest;
import com.robot.media.common.video.VideoSessionResponse;
import com.robot.media.common.video.ViewerTokenResponse;
import com.robot.media.common.video.IntercomResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 面向前端的视频会话控制接口入口。
 *
 * @author leelay
 * @date 2026-07-05
 */
@RestController
@RequestMapping("/api/control/video-sessions")
public class ControlVideoSessionController {

    private final ControlMediaServiceClient mediaServiceClient;
    private final ControlVideoCommandService controlVideoCommandService;
    private final CurrentUserResolver currentUserResolver;
    private final IntercomCallService intercomCallService;

    /**
     * 创建 ControlVideoSessionController 实例。
     *
     * @param mediaServiceClient Media Service 客户端
     * @param controlVideoCommandService 视频控制编排服务
     * @param currentUserResolver 当前用户解析器
     */
    public ControlVideoSessionController(
            ControlMediaServiceClient mediaServiceClient,
            ControlVideoCommandService controlVideoCommandService,
            CurrentUserResolver currentUserResolver,
            IntercomCallService intercomCallService) {
        this.mediaServiceClient = mediaServiceClient;
        this.controlVideoCommandService = controlVideoCommandService;
        this.currentUserResolver = currentUserResolver;
        this.intercomCallService = intercomCallService;
    }

    /**
     * 查询最近视频会话。
     *
     * @param servletRequest HTTP 请求
     * @return 最近视频会话列表
     */
    @GetMapping
    public List<VideoSessionResponse> recent(HttpServletRequest servletRequest) {
        CurrentUser user = currentUserResolver.resolve(servletRequest);
        return controlVideoCommandService.filterAuthorizedSessions(mediaServiceClient.recent(user));
    }

    /**
     * 查询活跃视频会话。
     *
     * @return 活跃视频会话列表
     */
    @GetMapping("/active")
    public List<VideoSessionResponse> active(HttpServletRequest servletRequest) {
        currentUserResolver.resolve(servletRequest);
        return controlVideoCommandService.filterAuthorizedSessions(mediaServiceClient.active());
    }

    /**
     * 查询指定资源。
     *
     * @param sessionId 会话 ID
     * @param servletRequest HTTP 请求
     * @return 查询结果
     */
    @GetMapping("/{sessionId}")
    public VideoSessionResponse get(@PathVariable String sessionId, HttpServletRequest servletRequest) {
        return controlVideoCommandService.requireAuthorizedSession(
                sessionId,
                currentUserResolver.resolve(servletRequest));
    }

    /**
     * 查询视频会话 Track。
     *
     * @param sessionId 会话 ID
     * @return Track 列表
     */
    @GetMapping("/{sessionId}/tracks")
    public List<MediaTrackResponse> tracks(@PathVariable String sessionId, HttpServletRequest servletRequest) {
        controlVideoCommandService.requireAuthorizedSession(
                sessionId,
                currentUserResolver.resolve(servletRequest));
        return mediaServiceClient.tracks(sessionId);
    }

    /**
     * 签发观看者 Token。
     *
     * @param sessionId 会话 ID
     * @param servletRequest HTTP 请求
     * @return 观看 Token 响应
     */
    @PostMapping("/{sessionId}/token")
    public ViewerTokenResponse token(@PathVariable String sessionId, HttpServletRequest servletRequest) {
        CurrentUser user = currentUserResolver.resolve(servletRequest);
        controlVideoCommandService.requireAuthorizedSession(sessionId, user);
        return mediaServiceClient.token(sessionId, user);
    }

    /**
     * 启动对讲。
     *
     * @param sessionId 会话 ID
     * @param servletRequest HTTP 请求
     * @return 对讲会话响应
     */
    @PostMapping("/{sessionId}/intercom/start")
    public IntercomResponse startIntercom(@PathVariable String sessionId, HttpServletRequest servletRequest) {
        CurrentUser user = currentUserResolver.resolve(servletRequest);
        VideoSessionResponse session = controlVideoCommandService.requireAuthorizedSession(sessionId, user);
        intercomCallService.requireManualIntercomAllowed(session.robotId());
        return controlVideoCommandService.startIntercom(sessionId, user);
    }

    /**
     * 签发对讲 Token。
     *
     * @param sessionId 会话 ID
     * @param servletRequest HTTP 请求
     * @return 对讲 Token 响应
     */
    @PostMapping("/{sessionId}/intercom/token")
    public IntercomResponse intercomToken(@PathVariable String sessionId, HttpServletRequest servletRequest) {
        CurrentUser user = currentUserResolver.resolve(servletRequest);
        controlVideoCommandService.requireAuthorizedSession(sessionId, user);
        return mediaServiceClient.intercomToken(sessionId, user);
    }

    /**
     * 刷新对讲心跳。
     *
     * @param sessionId 会话 ID
     * @param servletRequest HTTP 请求
     * @return 对讲心跳响应
     */
    @PostMapping("/{sessionId}/intercom/heartbeat")
    public IntercomResponse intercomHeartbeat(@PathVariable String sessionId, HttpServletRequest servletRequest) {
        CurrentUser user = currentUserResolver.resolve(servletRequest);
        controlVideoCommandService.requireAuthorizedSession(sessionId, user);
        return mediaServiceClient.intercomHeartbeat(sessionId, user);
    }

    /**
     * 停止对讲。
     *
     * @param sessionId 会话 ID
     * @param servletRequest HTTP 请求
     * @return 视频会话响应
     */
    @PostMapping("/{sessionId}/intercom/stop")
    public VideoSessionResponse stopIntercom(@PathVariable String sessionId, HttpServletRequest servletRequest) {
        VideoSessionResponse response = controlVideoCommandService.stopIntercom(sessionId, currentUserResolver.resolve(servletRequest));
        intercomCallService.endBySession(sessionId);
        return response;
    }

    /**
     * 刷新观看者心跳。
     *
     * @param sessionId 会话 ID
     * @param servletRequest HTTP 请求
     * @return 视频会话响应
     */
    @PostMapping("/{sessionId}/heartbeat")
    public VideoSessionResponse heartbeat(@PathVariable String sessionId, HttpServletRequest servletRequest) {
        CurrentUser user = currentUserResolver.resolve(servletRequest);
        controlVideoCommandService.requireAuthorizedSession(sessionId, user);
        return mediaServiceClient.heartbeat(sessionId, user);
    }

    /**
     * 停止当前观看者或会话。
     *
     * @param sessionId 会话 ID
     * @param servletRequest HTTP 请求
     * @return 视频会话响应
     */
    @PostMapping("/{sessionId}/stop")
    public VideoSessionResponse stop(@PathVariable String sessionId, HttpServletRequest servletRequest) {
        CurrentUser user = currentUserResolver.resolve(servletRequest);
        controlVideoCommandService.requireAuthorizedSession(sessionId, user);
        return mediaServiceClient.stop(sessionId, user);
    }

    /**
     * 重新请求机器人推流。
     *
     * @param sessionId 会话 ID
     * @param servletRequest HTTP 请求
     * @return 视频会话响应
     */
    @PostMapping("/{sessionId}/restart")
    public VideoSessionResponse restart(@PathVariable String sessionId, HttpServletRequest servletRequest) {
        return controlVideoCommandService.restartVideo(sessionId, currentUserResolver.resolve(servletRequest));
    }

    /**
     * 切换视频通道。
     *
     * @param sessionId 会话 ID
     * @param request 请求参数
     * @return 视频会话响应
     */
    @PostMapping("/{sessionId}/switch-channel")
    public VideoSessionResponse switchChannel(
            @PathVariable String sessionId,
            @Valid @RequestBody SwitchChannelRequest request,
            HttpServletRequest servletRequest) {
        return controlVideoCommandService.switchChannel(
                sessionId,
                request,
                currentUserResolver.resolve(servletRequest));
    }

    /**
     * 开始当前视频会话的直播录像。
     *
     * @param sessionId 会话 ID
     * @param servletRequest HTTP 请求
     * @return 录像文件信息
     */
    @PostMapping("/{sessionId}/recordings/start")
    public FileListItemResponse startRecording(@PathVariable String sessionId, HttpServletRequest servletRequest) {
        CurrentUser user = currentUserResolver.resolve(servletRequest);
        controlVideoCommandService.requireAuthorizedSession(sessionId, user);
        return mediaServiceClient.startLiveRecording(sessionId, user);
    }

    /**
     * 停止当前视频会话的直播录像。
     *
     * @param sessionId 会话 ID
     * @param fileId 文件 ID
     * @param servletRequest HTTP 请求
     * @return 录像文件信息
     */
    @PostMapping("/{sessionId}/recordings/{fileId}/stop")
    public FileListItemResponse stopRecording(
            @PathVariable String sessionId,
            @PathVariable String fileId,
            HttpServletRequest servletRequest) {
        CurrentUser user = currentUserResolver.resolve(servletRequest);
        controlVideoCommandService.requireAuthorizedSession(sessionId, user);
        return mediaServiceClient.stopLiveRecording(sessionId, fileId, user);
    }

    /**
     * 查询当前视频会话正在进行的直播录像。
     *
     * @param sessionId 会话 ID
     * @param servletRequest HTTP 请求
     * @return 活跃录像文件信息
     */
    @GetMapping("/{sessionId}/recordings/active")
    public FileListItemResponse activeRecording(@PathVariable String sessionId, HttpServletRequest servletRequest) {
        CurrentUser user = currentUserResolver.resolve(servletRequest);
        controlVideoCommandService.requireAuthorizedSession(sessionId, user);
        return mediaServiceClient.activeLiveRecording(sessionId, user);
    }
}
