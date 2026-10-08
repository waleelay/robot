package com.robot.mediaserver.video.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;

import com.robot.media.common.video.FixedCameraPublisherModeRequest;
import com.robot.media.common.video.FixedCameraPublisherModeResponse;
import com.robot.media.common.video.FixedCameraPublisherPresenceResponse;
import com.robot.mediaserver.video.service.VideoSessionService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Control 专用的固定摄像头发布端生命周期接口。 */
@RestController
@RequestMapping("/internal/media/fixed-camera-sources/{cameraId}")
public class FixedCameraSourceController {

    private static final String REVISION_HEADER = "X-Publisher-Revision";

    private final VideoSessionService service;

    /**
     * 允许执行固定摄像头发布端生命周期操作的内部调用方标识；依赖受控网络防止伪造请求头。
     */
    @Value("${media.fixed-camera-publisher.trusted-caller:control-service}")
    private String trustedCaller = "control-service";

    /**
     * 初始化 FixedCameraSourceController，保存所需依赖及初始运行状态。
     *
     * @param service 实时视频会话编排服务。
     */
    public FixedCameraSourceController(VideoSessionService service) {
        this.service = service;
    }

    /**
     * 切换固定摄像头发布模式并返回旧发布者停止命令；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param cameraId 固定摄像头 ID
     * @param publisherRevision 发布模式版本，用于拒绝陈旧发布者操作
     * @param request 请求参数
     * @param servletRequest HTTP 请求
     * @return 切换固定摄像头发布模式并返回旧发布者停止命令的接口响应
     */
    @Operation(
            operationId = "fixedCameraSourceController_switchPublisherMode",
            summary = "切换固定摄像头发布模式并返回旧发布者停止命令",
            description = "切换固定摄像头发布模式并返回旧发布者停止命令。受控 Media 服务入口；会话运行状态仍需客户端消息和 LiveKit 轨道事实确认。",
            tags = {"FixedCameraSourceController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @PostMapping("/publisher-mode")
    public FixedCameraPublisherModeResponse switchPublisherMode(
            @PathVariable String cameraId,
            @RequestHeader(REVISION_HEADER) long publisherRevision,
            @Valid @RequestBody FixedCameraPublisherModeRequest request,
            HttpServletRequest servletRequest) {
        requireTrustedCaller(servletRequest);
        return service.switchFixedCameraPublisherMode(cameraId, request.targetMode(), publisherRevision);
    }

    /**
     * 静默指定版本的固定摄像头发布者；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param cameraId 固定摄像头 ID
     * @param publisherRevision 发布模式版本，用于拒绝陈旧发布者操作
     * @param servletRequest HTTP 请求
     * @return 静默指定版本的固定摄像头发布者的接口响应
     */
    @Operation(
            operationId = "fixedCameraSourceController_quiesce",
            summary = "静默指定版本的固定摄像头发布者",
            description = "静默指定版本的固定摄像头发布者。受控 Media 服务入口；会话运行状态仍需客户端消息和 LiveKit 轨道事实确认。",
            tags = {"FixedCameraSourceController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @PostMapping("/quiesce")
    public FixedCameraPublisherModeResponse quiesce(
            @PathVariable String cameraId,
            @RequestHeader(REVISION_HEADER) long publisherRevision,
            HttpServletRequest servletRequest) {
        requireTrustedCaller(servletRequest);
        return service.quiesceFixedCameraPublisher(cameraId, publisherRevision);
    }

    /**
     * 查询房间内预期发布者与视频轨道是否存在；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param cameraId 固定摄像头 ID
     * @param servletRequest HTTP 请求
     * @return 查询房间内预期发布者与视频轨道是否存在的接口响应
     */
    @Operation(
            operationId = "fixedCameraSourceController_publisherPresence",
            summary = "查询房间内预期发布者与视频轨道是否存在",
            description = "查询房间内预期发布者与视频轨道是否存在。受控 Media 服务入口；会话运行状态仍需客户端消息和 LiveKit 轨道事实确认。",
            tags = {"FixedCameraSourceController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @GetMapping("/publisher-presence")
    public FixedCameraPublisherPresenceResponse publisherPresence(
            @PathVariable String cameraId,
            HttpServletRequest servletRequest) {
        requireTrustedCaller(servletRequest);
        return service.fixedCameraPublisherPresence(cameraId);
    }

    private void requireTrustedCaller(HttpServletRequest request) {
        if (!trustedCaller.equals(request.getHeader("X-Internal-Caller"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "固定摄像头发布端调用方不受信任");
        }
    }
}
