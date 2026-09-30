package com.robot.mediaserver.video.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;

import com.robot.mediaserver.auth.CurrentUser;
import com.robot.mediaserver.auth.CurrentUserResolver;
import com.robot.media.common.file.FileListItemResponse;
import com.robot.media.common.video.CreateVideoSessionRequest;
import com.robot.media.common.video.MediaTrackResponse;
import com.robot.media.common.video.IntercomResponse;
import com.robot.media.common.video.SwitchChannelRequest;
import com.robot.media.common.video.VideoSessionResponse;
import com.robot.media.common.video.ViewerTokenResponse;
import com.robot.media.common.video.VideoStartCommand;
import com.robot.media.common.video.VideoStatusMessage;
import com.robot.media.common.video.IntercomStartCommand;
import com.robot.media.common.video.IntercomStatusMessage;
import com.robot.mediaserver.video.service.MediaTrackService;
import com.robot.mediaserver.video.service.VideoSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 提供视频会话及观看端、轨道、对讲和录像的内部 HTTP 入口。 */
@RestController
@RequestMapping("/internal/media/video-sessions")
public class VideoSessionController {

    private static final Logger log = LoggerFactory.getLogger(VideoSessionController.class);

    private final VideoSessionService service;
    private final CurrentUserResolver currentUserResolver;
    private final MediaTrackService mediaTrackService;

    /**
     * 初始化 VideoSessionController，保存所需依赖及初始运行状态。
     *
     * @param service 实时视频会话编排服务。
     * @param currentUserResolver 当前用户解析器
     * @param mediaTrackService 媒体轨道服务。
     */
    public VideoSessionController(
            VideoSessionService service,
            CurrentUserResolver currentUserResolver,
            MediaTrackService mediaTrackService) {
        this.service = service;
        this.currentUserResolver = currentUserResolver;
        this.mediaTrackService = mediaTrackService;
    }

    /**
     * 创建或复用视频会话及观看者租约；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param request 请求参数
     * @param servletRequest HTTP 请求
     * @return 创建或复用视频会话及观看者租约的接口响应
     */
    @Operation(
            operationId = "videoSessionController_create",
            summary = "创建或复用视频会话及观看者租约",
            description = "创建或复用视频会话及观看者租约。受控 Media 服务入口；会话运行状态仍需客户端消息和 LiveKit 轨道事实确认。",
            tags = {"VideoSessionController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @PostMapping
    public VideoSessionResponse create(@Valid @RequestBody CreateVideoSessionRequest request, HttpServletRequest servletRequest) {
        log.info("创建视频会话 机器人标识={}, 设备标识={}, 通道={}, 清晰度={}",
                request.getRobotId(),
                request.getDeviceId(),
                request.getChannel(),
                request.getQuality());
        return service.create(request, currentUserResolver.resolve(servletRequest));
    }

    /**
     * 创建或复用仅音频对讲会话；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param request 请求参数
     * @param servletRequest HTTP 请求
     * @return 创建或复用仅音频对讲会话的接口响应
     */
    @Operation(
            operationId = "videoSessionController_createIntercom",
            summary = "创建或复用仅音频对讲会话",
            description = "创建或复用仅音频对讲会话。受控 Media 服务入口；会话运行状态仍需客户端消息和 LiveKit 轨道事实确认。",
            tags = {"VideoSessionController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @PostMapping("/intercom")
    public IntercomResponse createIntercom(
            @Valid @RequestBody CreateVideoSessionRequest request,
            HttpServletRequest servletRequest) {
        return service.createForIntercom(request, currentUserResolver.resolve(servletRequest));
    }

    /**
     * 查询当前用户近期视频会话；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param servletRequest HTTP 请求
     * @return 查询当前用户近期视频会话的接口响应
     */
    @Operation(
            operationId = "videoSessionController_recent",
            summary = "查询当前用户近期视频会话",
            description = "查询当前用户近期视频会话。受控 Media 服务入口；会话运行状态仍需客户端消息和 LiveKit 轨道事实确认。",
            tags = {"VideoSessionController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @GetMapping
    public List<VideoSessionResponse> recent(HttpServletRequest servletRequest) {
        return service.recent(currentUserResolver.resolve(servletRequest));
    }

    /**
     * 查询媒体服务当前活动会话；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @return 查询媒体服务当前活动会话的接口响应
     */
    @Operation(
            operationId = "videoSessionController_active",
            summary = "查询媒体服务当前活动会话",
            description = "查询媒体服务当前活动会话。受控 Media 服务入口；会话运行状态仍需客户端消息和 LiveKit 轨道事实确认。",
            tags = {"VideoSessionController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @GetMapping("/active")
    public List<VideoSessionResponse> active() {
        return service.active();
    }

    /**
     * 查询早于指定时间且需重启的中断会话；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param before 时间阈值
     * @return 查询早于指定时间且需重启的中断会话的接口响应
     */
    @Operation(
            operationId = "videoSessionController_interruptedRestartCandidates",
            summary = "查询早于指定时间且需重启的中断会话",
            description = "查询早于指定时间且需重启的中断会话。受控 Media 服务入口；会话运行状态仍需客户端消息和 LiveKit 轨道事实确认。",
            tags = {"VideoSessionController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @GetMapping("/interrupted-restart-candidates")
    public List<String> interruptedRestartCandidates(@RequestParam OffsetDateTime before) {
        return service.interruptedRestartCandidates(before);
    }

    /**
     * 查询早于指定时间且可释放的空闲会话；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param before 时间阈值
     * @return 查询早于指定时间且可释放的空闲会话的接口响应
     */
    @Operation(
            operationId = "videoSessionController_idleReleaseCandidates",
            summary = "查询早于指定时间且可释放的空闲会话",
            description = "查询早于指定时间且可释放的空闲会话。受控 Media 服务入口；会话运行状态仍需客户端消息和 LiveKit 轨道事实确认。",
            tags = {"VideoSessionController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @GetMapping("/idle-release-candidates")
    public List<String> idleReleaseCandidates(@RequestParam OffsetDateTime before) {
        return service.idleReleaseCandidates(before);
    }

    /**
     * 查询对讲心跳已超时的会话；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param before 时间阈值
     * @return 查询对讲心跳已超时的会话的接口响应
     */
    @Operation(
            operationId = "videoSessionController_intercomTimeoutCandidates",
            summary = "查询对讲心跳已超时的会话",
            description = "查询对讲心跳已超时的会话。受控 Media 服务入口；会话运行状态仍需客户端消息和 LiveKit 轨道事实确认。",
            tags = {"VideoSessionController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @GetMapping("/intercom-timeout-candidates")
    public List<String> intercomTimeoutCandidates(@RequestParam OffsetDateTime before) {
        return service.intercomTimeoutCandidates(before);
    }

    /**
     * 查询视频会话和当前观看者状态；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param sessionId 会话 ID
     * @param servletRequest HTTP 请求
     * @return 查询视频会话和当前观看者状态的接口响应
     */
    @Operation(
            operationId = "videoSessionController_get",
            summary = "查询视频会话和当前观看者状态",
            description = "查询视频会话和当前观看者状态。受控 Media 服务入口；会话运行状态仍需客户端消息和 LiveKit 轨道事实确认。",
            tags = {"VideoSessionController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @GetMapping("/{sessionId}")
    public VideoSessionResponse get(@PathVariable String sessionId, HttpServletRequest servletRequest) {
        return service.get(sessionId, currentUserResolver.resolve(servletRequest));
    }

    /**
     * 查询会话最近的媒体轨道记录；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param sessionId 会话 ID
     * @return 查询会话最近的媒体轨道记录的接口响应
     */
    @Operation(
            operationId = "videoSessionController_tracks",
            summary = "查询会话最近的媒体轨道记录",
            description = "查询会话最近的媒体轨道记录。受控 Media 服务入口；会话运行状态仍需客户端消息和 LiveKit 轨道事实确认。",
            tags = {"VideoSessionController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @GetMapping("/{sessionId}/tracks")
    public List<MediaTrackResponse> tracks(@PathVariable String sessionId) {
        return mediaTrackService.recentBySession(sessionId);
    }

    /**
     * 接收客户端视频状态并推进媒体会话状态；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param status 当前业务状态，取值遵循所属模型的状态协议
     */
    @Operation(
            operationId = "videoSessionController_status",
            summary = "接收客户端视频状态并推进媒体会话状态",
            description = "接收客户端视频状态并推进媒体会话状态。受控 Media 服务入口；会话运行状态仍需客户端消息和 LiveKit 轨道事实确认。",
            tags = {"VideoSessionController"})
    @ApiResponse(responseCode = "200", description = "处理完成，无响应正文", content = @Content)
    @PostMapping("/status")
    public void status(@RequestBody VideoStatusMessage status) {
        service.handleClientStatus(
                status.getSessionId(),
                status.getStatus(),
                status.getTrackSid(),
                status.getTrackName(),
                status.getErrorCode(),
                status.getMessage());
    }

    /**
     * 接收机器人对讲音频状态；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param status 当前业务状态，取值遵循所属模型的状态协议
     */
    @Operation(
            operationId = "videoSessionController_intercomStatus",
            summary = "接收机器人对讲音频状态",
            description = "接收机器人对讲音频状态。受控 Media 服务入口；会话运行状态仍需客户端消息和 LiveKit 轨道事实确认。",
            tags = {"VideoSessionController"})
    @ApiResponse(responseCode = "200", description = "处理完成，无响应正文", content = @Content)
    @PostMapping("/intercom/status")
    public void intercomStatus(@RequestBody IntercomStatusMessage status) {
        service.handleIntercomStatus(
                status.getSessionId(),
                status.getStatus(),
                status.getRobotAudioTrackSid(),
                status.getRobotAudioTrackName(),
                status.getErrorCode(),
                status.getMessage());
    }

    /**
     * 为当前观看者签发房间访问令牌；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param sessionId 会话 ID
     * @param servletRequest HTTP 请求
     * @return 为当前观看者签发房间访问令牌的接口响应
     */
    @Operation(
            operationId = "videoSessionController_token",
            summary = "为当前观看者签发房间访问令牌",
            description = "为当前观看者签发房间访问令牌。受控 Media 服务入口；会话运行状态仍需客户端消息和 LiveKit 轨道事实确认。",
            tags = {"VideoSessionController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @PostMapping("/{sessionId}/token")
    public ViewerTokenResponse token(@PathVariable String sessionId, HttpServletRequest servletRequest) {
        CurrentUser user = currentUserResolver.resolve(servletRequest);
        return service.createViewerToken(sessionId, user);
    }

    /**
     * 获取会话对讲占用并签发操作端令牌；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param sessionId 会话 ID
     * @param servletRequest HTTP 请求
     * @return 获取会话对讲占用并签发操作端令牌的接口响应
     */
    @Operation(
            operationId = "videoSessionController_startIntercom",
            summary = "获取会话对讲占用并签发操作端令牌",
            description = "获取会话对讲占用并签发操作端令牌。受控 Media 服务入口；会话运行状态仍需客户端消息和 LiveKit 轨道事实确认。",
            tags = {"VideoSessionController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @PostMapping("/{sessionId}/intercom/start")
    public IntercomResponse startIntercom(@PathVariable String sessionId, HttpServletRequest servletRequest) {
        return service.startIntercom(sessionId, currentUserResolver.resolve(servletRequest));
    }

    /**
     * 刷新当前操作端的对讲令牌；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param sessionId 会话 ID
     * @param servletRequest HTTP 请求
     * @return 刷新当前操作端的对讲令牌的接口响应
     */
    @Operation(
            operationId = "videoSessionController_intercomToken",
            summary = "刷新当前操作端的对讲令牌",
            description = "刷新当前操作端的对讲令牌。受控 Media 服务入口；会话运行状态仍需客户端消息和 LiveKit 轨道事实确认。",
            tags = {"VideoSessionController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @PostMapping("/{sessionId}/intercom/token")
    public IntercomResponse intercomToken(@PathVariable String sessionId, HttpServletRequest servletRequest) {
        return service.createIntercomToken(sessionId, currentUserResolver.resolve(servletRequest));
    }

    /**
     * 续期当前操作端的对讲占用；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param sessionId 会话 ID
     * @param servletRequest HTTP 请求
     * @return 续期当前操作端的对讲占用的接口响应
     */
    @Operation(
            operationId = "videoSessionController_intercomHeartbeat",
            summary = "续期当前操作端的对讲占用",
            description = "续期当前操作端的对讲占用。受控 Media 服务入口；会话运行状态仍需客户端消息和 LiveKit 轨道事实确认。",
            tags = {"VideoSessionController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @PostMapping("/{sessionId}/intercom/heartbeat")
    public IntercomResponse intercomHeartbeat(@PathVariable String sessionId, HttpServletRequest servletRequest) {
        return service.heartbeatIntercom(sessionId, currentUserResolver.resolve(servletRequest));
    }

    /**
     * 结束当前操作端的对讲并释放占用；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param sessionId 会话 ID
     * @param servletRequest HTTP 请求
     * @return 结束当前操作端的对讲并释放占用的接口响应
     */
    @Operation(
            operationId = "videoSessionController_stopIntercom",
            summary = "结束当前操作端的对讲并释放占用",
            description = "结束当前操作端的对讲并释放占用。受控 Media 服务入口；会话运行状态仍需客户端消息和 LiveKit 轨道事实确认。",
            tags = {"VideoSessionController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @PostMapping("/{sessionId}/intercom/stop")
    public VideoSessionResponse stopIntercom(@PathVariable String sessionId, HttpServletRequest servletRequest) {
        return service.stopIntercom(sessionId, currentUserResolver.resolve(servletRequest));
    }

    /**
     * 收口已超时的对讲占用；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param sessionId 会话 ID
     * @return 收口已超时的对讲占用的接口响应
     */
    @Operation(
            operationId = "videoSessionController_expireIntercom",
            summary = "收口已超时的对讲占用",
            description = "收口已超时的对讲占用。受控 Media 服务入口；会话运行状态仍需客户端消息和 LiveKit 轨道事实确认。",
            tags = {"VideoSessionController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @PostMapping("/{sessionId}/intercom/expire")
    public java.util.Map<String, Object> expireIntercom(@PathVariable String sessionId) {
        return service.expireIntercom(sessionId);
    }

    /**
     * 生成机器人对讲启动指令；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param sessionId 会话 ID
     * @return 生成机器人对讲启动指令的接口响应
     */
    @Operation(
            operationId = "videoSessionController_intercomStartCommand",
            summary = "生成机器人对讲启动指令",
            description = "生成机器人对讲启动指令。受控 Media 服务入口；会话运行状态仍需客户端消息和 LiveKit 轨道事实确认。",
            tags = {"VideoSessionController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @PostMapping("/{sessionId}/intercom/start-command")
    public IntercomStartCommand intercomStartCommand(@PathVariable String sessionId) {
        return service.createIntercomStartCommand(sessionId);
    }

    /**
     * 续期观看者租约并返回最新会话；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param sessionId 会话 ID
     * @param servletRequest HTTP 请求
     * @return 续期观看者租约并返回最新会话的接口响应
     */
    @Operation(
            operationId = "videoSessionController_heartbeat",
            summary = "续期观看者租约并返回最新会话",
            description = "续期观看者租约并返回最新会话。受控 Media 服务入口；会话运行状态仍需客户端消息和 LiveKit 轨道事实确认。",
            tags = {"VideoSessionController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @PostMapping("/{sessionId}/heartbeat")
    public VideoSessionResponse heartbeat(@PathVariable String sessionId, HttpServletRequest servletRequest) {
        return service.heartbeat(sessionId, currentUserResolver.resolve(servletRequest));
    }

    /**
     * 停止会话或释放当前观看者；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param sessionId 会话 ID
     * @param servletRequest HTTP 请求
     * @return 停止会话或释放当前观看者的接口响应
     */
    @Operation(
            operationId = "videoSessionController_stop",
            summary = "停止会话或释放当前观看者",
            description = "停止会话或释放当前观看者。受控 Media 服务入口；会话运行状态仍需客户端消息和 LiveKit 轨道事实确认。",
            tags = {"VideoSessionController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @PostMapping("/{sessionId}/stop")
    public VideoSessionResponse stop(@PathVariable String sessionId, HttpServletRequest servletRequest) {
        return service.stop(sessionId, currentUserResolver.resolve(servletRequest));
    }

    /**
     * 请求重启媒体会话；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param sessionId 会话 ID
     * @param servletRequest HTTP 请求
     * @return 请求重启媒体会话的接口响应
     */
    @Operation(
            operationId = "videoSessionController_restart",
            summary = "请求重启媒体会话",
            description = "请求重启媒体会话。受控 Media 服务入口；会话运行状态仍需客户端消息和 LiveKit 轨道事实确认。",
            tags = {"VideoSessionController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @PostMapping("/{sessionId}/restart")
    public VideoSessionResponse restart(@PathVariable String sessionId, HttpServletRequest servletRequest) {
        return service.restartSession(sessionId, currentUserResolver.resolve(servletRequest));
    }

    /**
     * 重启会话并返回待发布给客户端的启动指令；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param sessionId 会话 ID
     * @param servletRequest HTTP 请求
     * @return 重启会话并返回待发布给客户端的启动指令的接口响应
     */
    @Operation(
            operationId = "videoSessionController_restartCommand",
            summary = "重启会话并返回待发布给客户端的启动指令",
            description = "重启会话并返回待发布给客户端的启动指令。受控 Media 服务入口；会话运行状态仍需客户端消息和 LiveKit 轨道事实确认。",
            tags = {"VideoSessionController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @PostMapping("/{sessionId}/restart-command")
    public VideoStartCommand restartCommand(@PathVariable String sessionId, HttpServletRequest servletRequest) {
        return service.restartSessionCommand(sessionId, currentUserResolver.resolve(servletRequest));
    }

    /**
     * 标记客户端启动请求并生成启动指令；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param sessionId 会话 ID
     * @param event 事件名称
     * @return 标记客户端启动请求并生成启动指令的接口响应
     */
    @Operation(
            operationId = "videoSessionController_clientStart",
            summary = "标记客户端启动请求并生成启动指令",
            description = "标记客户端启动请求并生成启动指令。受控 Media 服务入口；会话运行状态仍需客户端消息和 LiveKit 轨道事实确认。",
            tags = {"VideoSessionController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @PostMapping("/{sessionId}/client-start")
    public VideoStartCommand clientStart(@PathVariable String sessionId, @RequestParam(defaultValue = "video.client.requested") String event) {
        return service.requestClientStart(sessionId, event);
    }

    /**
     * 生成当前会话的客户端启动指令；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param sessionId 会话 ID
     * @return 生成当前会话的客户端启动指令的接口响应
     */
    @Operation(
            operationId = "videoSessionController_startCommand",
            summary = "生成当前会话的客户端启动指令",
            description = "生成当前会话的客户端启动指令。受控 Media 服务入口；会话运行状态仍需客户端消息和 LiveKit 轨道事实确认。",
            tags = {"VideoSessionController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @PostMapping("/{sessionId}/start-command")
    public VideoStartCommand startCommand(@PathVariable String sessionId) {
        return service.createStartCommand(sessionId);
    }

    /**
     * 根据客户端在线状态生成待恢复视频指令；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param robotId 机器人 ID
     * @param status 当前业务状态，取值遵循所属模型的状态协议
     * @return 根据客户端在线状态生成待恢复视频指令的接口响应
     */
    @Operation(
            operationId = "videoSessionController_onlineRestartCommands",
            summary = "根据客户端在线状态生成待恢复视频指令",
            description = "根据客户端在线状态生成待恢复视频指令。受控 Media 服务入口；会话运行状态仍需客户端消息和 LiveKit 轨道事实确认。",
            tags = {"VideoSessionController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @PostMapping("/online-restart-commands")
    public List<VideoStartCommand> onlineRestartCommands(@RequestParam String robotId, @RequestParam String status) {
        return service.handleClientOnline(robotId, status);
    }

    /**
     * 为固定摄像头恢复场景生成重启指令；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     * @param sourceId 待恢复的固定摄像头 ID；为空时检查全部符合条件的固定摄像头来源
     * @param gatewayReconnect 是否由 Gateway 离线转在线触发
     * @return 为固定摄像头恢复场景生成重启指令的接口响应
     */
    @Operation(
            operationId = "videoSessionController_fixedCameraRecoveryCommands",
            summary = "为固定摄像头恢复场景生成重启指令",
            description = "为固定摄像头恢复场景生成重启指令。受控 Media 服务入口；会话运行状态仍需客户端消息和 LiveKit 轨道事实确认。",
            tags = {"VideoSessionController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @PostMapping("/fixed-camera-recovery-commands")
    public List<VideoStartCommand> fixedCameraRecoveryCommands(
            @RequestParam(required = false) String sourceId,
            @RequestParam(defaultValue = "false") boolean gatewayReconnect) {
        return service.fixedCameraRecoveryCommands(sourceId, gatewayReconnect);
    }

    /**
     * 收口满足空闲释放条件的视频会话；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param sessionId 会话 ID
     * @return 收口满足空闲释放条件的视频会话的接口响应
     */
    @Operation(
            operationId = "videoSessionController_releaseIdle",
            summary = "收口满足空闲释放条件的视频会话",
            description = "收口满足空闲释放条件的视频会话。受控 Media 服务入口；会话运行状态仍需客户端消息和 LiveKit 轨道事实确认。",
            tags = {"VideoSessionController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @PostMapping("/{sessionId}/release-idle")
    public java.util.Map<String, Object> releaseIdle(@PathVariable String sessionId) {
        return service.releaseIdleSession(sessionId);
    }

    /**
     * 切换会话通道和清晰度；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param sessionId 会话 ID
     * @param request 请求参数
     * @return 切换会话通道和清晰度的接口响应
     */
    @Operation(
            operationId = "videoSessionController_switchChannel",
            summary = "切换会话通道和清晰度",
            description = "切换会话通道和清晰度。受控 Media 服务入口；会话运行状态仍需客户端消息和 LiveKit 轨道事实确认。",
            tags = {"VideoSessionController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @PostMapping("/{sessionId}/switch-channel")
    public VideoSessionResponse switchChannel(@PathVariable String sessionId, @Valid @RequestBody SwitchChannelRequest request) {
        return service.switchChannel(sessionId, request);
    }

    /**
     * 启动会话实时录像并返回文件记录；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param sessionId 会话 ID
     * @param servletRequest HTTP 请求
     * @return 启动会话实时录像并返回文件记录的接口响应
     */
    @Operation(
            operationId = "videoSessionController_startRecording",
            summary = "启动会话实时录像并返回文件记录",
            description = "启动会话实时录像并返回文件记录。受控 Media 服务入口；会话运行状态仍需客户端消息和 LiveKit 轨道事实确认。",
            tags = {"VideoSessionController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @PostMapping("/{sessionId}/recordings/start")
    public FileListItemResponse startRecording(@PathVariable String sessionId, HttpServletRequest servletRequest) {
        return service.startRecording(sessionId, currentUserResolver.resolve(servletRequest));
    }

    /**
     * 请求停止实时录像，文件就绪由后续处理确认；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param sessionId 会话 ID
     * @param fileId 文件 ID
     * @param servletRequest HTTP 请求
     * @return 请求停止实时录像，文件就绪由后续处理确认的接口响应
     */
    @Operation(
            operationId = "videoSessionController_stopRecording",
            summary = "请求停止实时录像，文件就绪由后续处理确认",
            description = "请求停止实时录像，文件就绪由后续处理确认。受控 Media 服务入口；会话运行状态仍需客户端消息和 LiveKit 轨道事实确认。",
            tags = {"VideoSessionController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @PostMapping("/{sessionId}/recordings/{fileId}/stop")
    public FileListItemResponse stopRecording(
            @PathVariable String sessionId,
            @PathVariable String fileId,
            HttpServletRequest servletRequest) {
        return service.stopRecording(sessionId, fileId, currentUserResolver.resolve(servletRequest));
    }

    /**
     * 查询当前会话的活动录像，没有活动录像时正文为空；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param sessionId 会话 ID
     * @param servletRequest HTTP 请求
     * @return 查询当前会话的活动录像，没有活动录像时正文为空的接口响应
     */
    @Operation(
            operationId = "videoSessionController_activeRecording",
            summary = "查询当前会话的活动录像，没有活动录像时正文为空",
            description = "查询当前会话的活动录像，没有活动录像时正文为空。受控 Media 服务入口；会话运行状态仍需客户端消息和 LiveKit 轨道事实确认。",
            tags = {"VideoSessionController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @GetMapping("/{sessionId}/recordings/active")
    public FileListItemResponse activeRecording(@PathVariable String sessionId, HttpServletRequest servletRequest) {
        return service.activeRecording(sessionId, currentUserResolver.resolve(servletRequest));
    }

}
