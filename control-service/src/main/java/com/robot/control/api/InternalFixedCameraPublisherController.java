package com.robot.control.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;

import com.robot.control.fixedcamera.FixedCameraPublisherLifecycleService;
import com.robot.media.common.video.FixedCameraPublisherModeResponse;
import com.robot.media.common.video.VideoPublisherMode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Management 调用的固定摄像头发布端生命周期入口。 */
@RestController
@RequestMapping("/internal/control/fixed-cameras/{cameraId}")
public class InternalFixedCameraPublisherController {

    private final FixedCameraPublisherLifecycleService service;

    /**
     * 允许执行发布端生命周期操作的 Management 调用方标识。
     */
    @Value("${control.fixed-camera-publisher.trusted-caller:management-service}")
    private String trustedCaller = "management-service";

    /**
     * 初始化 InternalFixedCameraPublisherController，保存所需依赖及初始运行状态。
     *
     * @param service 编排 Media 发布模式与固定摄像头 Gateway 停止命令。
     */
    public InternalFixedCameraPublisherController(FixedCameraPublisherLifecycleService service) {
        this.service = service;
    }

    /**
     * 校验发布版本后编排固定摄像头发布模式切换；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param cameraId 固定摄像头 ID
     * @param request 请求参数
     * @param servletRequest HTTP 请求
     * @return 校验发布版本后编排固定摄像头发布模式切换的接口响应
     */
    @Operation(
            operationId = "internalFixedCameraPublisherController_switchMode",
            summary = "校验发布版本后编排固定摄像头发布模式切换",
            description = "校验发布版本后编排固定摄像头发布模式切换。受控服务入口；使用用户上下文的方法依赖可信上游 Header，编排成功不等于设备已完成动作。",
            tags = {"InternalFixedCameraPublisherController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @PostMapping("/publisher-mode-switch")
    public FixedCameraPublisherModeResponse switchMode(
            @PathVariable String cameraId,
            @Valid @RequestBody ModeSwitchRequest request,
            HttpServletRequest servletRequest) {
        requireTrustedCaller(servletRequest);
        return service.switchMode(cameraId, request.targetMode(), request.publisherRevision());
    }

    /**
     * 删除摄像头前停止对应版本的发布者；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param cameraId 固定摄像头 ID
     * @param request 请求参数
     * @param servletRequest HTTP 请求
     * @return 删除摄像头前停止对应版本的发布者的接口响应
     */
    @Operation(
            operationId = "internalFixedCameraPublisherController_quiesce",
            summary = "删除摄像头前停止对应版本的发布者",
            description = "删除摄像头前停止对应版本的发布者。受控服务入口；使用用户上下文的方法依赖可信上游 Header，编排成功不等于设备已完成动作。",
            tags = {"InternalFixedCameraPublisherController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @PostMapping("/publisher-quiesce")
    public FixedCameraPublisherModeResponse quiesce(
            @PathVariable String cameraId,
            @Valid @RequestBody QuiesceRequest request,
            HttpServletRequest servletRequest) {
        requireTrustedCaller(servletRequest);
        if (!"DELETE".equals(request.reason())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "固定摄像头收口原因不合法");
        }
        return service.quiesceForDelete(cameraId, request.publisherRevision());
    }

    private void requireTrustedCaller(HttpServletRequest request) {
        if (!trustedCaller.equals(request.getHeader("X-Internal-Caller"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "固定摄像头发布编排调用方不受信任");
        }
    }

    /**
     * 请求切换固定摄像头发布模式，并携带发布者版本防止陈旧操作。
     *
     * @param targetMode 目标发布模式
     * @param publisherRevision 本次发布生命周期修订号，防止旧指令覆盖新状态
     */
    public record ModeSwitchRequest(@Schema(description = "目标发布模式", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull VideoPublisherMode targetMode, @Schema(description = "本次发布生命周期修订号，防止旧指令覆盖新状态") long publisherRevision) {
    }

    /**
     * 请求静默旧发布者，附带发布者版本和操作原因。
     *
     * @param publisherRevision 本次发布生命周期修订号，防止旧指令覆盖新状态
     * @param reason 静默原因，当前仅接受 DELETE
     */
    public record QuiesceRequest(@Schema(description = "本次发布生命周期修订号，防止旧指令覆盖新状态") long publisherRevision, @Schema(description = "静默原因，当前仅接受 DELETE", allowableValues = "DELETE", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull String reason) {
    }
}
