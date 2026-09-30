package com.robot.mediaserver.video.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;

import com.robot.media.common.video.FixedCameraIngressResponse;
import com.robot.media.common.video.FixedCameraIngressStatusRequest;
import com.robot.mediaserver.video.service.FixedCameraIngressService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Media 内部固定摄像头 Ingress 管理接口。 */
@RestController
@RequestMapping("/internal/media/fixed-camera-ingresses")
public class FixedCameraIngressController {

    private static final String REVISION_HEADER = "X-Ingress-Operation-Revision";

    private final FixedCameraIngressService service;

    /**
     * 初始化 FixedCameraIngressController，保存所需依赖及初始运行状态。
     *
     * @param service 固定摄像头 RTMP Ingress 配置生命周期。
     */
    public FixedCameraIngressController(FixedCameraIngressService service) {
        this.service = service;
    }

    /**
     * 为固定摄像头创建或复用 Ingress；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param request 请求参数
     * @param revision 调用方观察到的状态版本，用于防止陈旧操作
     * @return 为固定摄像头创建或复用 Ingress的接口响应
     */
    @Operation(
            operationId = "fixedCameraIngressController_create",
            summary = "为固定摄像头创建或复用 Ingress",
            description = "为固定摄像头创建或复用 Ingress。受控 Media 服务入口；会话运行状态仍需客户端消息和 LiveKit 轨道事实确认。",
            tags = {"FixedCameraIngressController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @PostMapping
    public FixedCameraIngressResponse create(
            @Valid @RequestBody CameraRequest request,
            @RequestHeader(REVISION_HEADER) long revision) {
        return service.create(request.cameraId(), revision);
    }

    /**
     * 查询固定摄像头 Ingress 配置和运行状态；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param cameraId 固定摄像头 ID
     * @return 查询固定摄像头 Ingress 配置和运行状态的接口响应
     */
    @Operation(
            operationId = "fixedCameraIngressController_get",
            summary = "查询固定摄像头 Ingress 配置和运行状态",
            description = "查询固定摄像头 Ingress 配置和运行状态。受控 Media 服务入口；会话运行状态仍需客户端消息和 LiveKit 轨道事实确认。",
            tags = {"FixedCameraIngressController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @GetMapping("/{cameraId}")
    public FixedCameraIngressResponse get(@PathVariable String cameraId) {
        return service.get(cameraId);
    }

    /**
     * 轮换固定摄像头 Ingress 凭据并提升操作版本；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param cameraId 固定摄像头 ID
     * @param revision 调用方观察到的状态版本，用于防止陈旧操作
     * @return 轮换固定摄像头 Ingress 凭据并提升操作版本的接口响应
     */
    @Operation(
            operationId = "fixedCameraIngressController_rotate",
            summary = "轮换固定摄像头 Ingress 凭据并提升操作版本",
            description = "轮换固定摄像头 Ingress 凭据并提升操作版本。受控 Media 服务入口；会话运行状态仍需客户端消息和 LiveKit 轨道事实确认。",
            tags = {"FixedCameraIngressController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @PutMapping("/{cameraId}")
    public FixedCameraIngressResponse rotate(
            @PathVariable String cameraId,
            @RequestHeader(REVISION_HEADER) long revision) {
        return service.rotate(cameraId, revision);
    }

    /**
     * 撤销固定摄像头 Ingress；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param cameraId 固定摄像头 ID
     * @param revision 调用方观察到的状态版本，用于防止陈旧操作
     */
    @Operation(
            operationId = "fixedCameraIngressController_revoke",
            summary = "撤销固定摄像头 Ingress",
            description = "撤销固定摄像头 Ingress。受控 Media 服务入口；会话运行状态仍需客户端消息和 LiveKit 轨道事实确认。",
            tags = {"FixedCameraIngressController"})
    @ApiResponse(responseCode = "200", description = "处理完成，无响应正文", content = @Content)
    @DeleteMapping("/{cameraId}")
    public void revoke(
            @PathVariable String cameraId,
            @RequestHeader(REVISION_HEADER) long revision) {
        service.revoke(cameraId, revision);
    }

    /**
     * 批量查询固定摄像头 Ingress 状态；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param request 请求参数
     * @return 批量查询固定摄像头 Ingress 状态的接口响应
     */
    @Operation(
            operationId = "fixedCameraIngressController_statuses",
            summary = "批量查询固定摄像头 Ingress 状态",
            description = "批量查询固定摄像头 Ingress 状态。受控 Media 服务入口；会话运行状态仍需客户端消息和 LiveKit 轨道事实确认。",
            tags = {"FixedCameraIngressController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @PostMapping("/status-query")
    public List<FixedCameraIngressResponse> statuses(@RequestBody FixedCameraIngressStatusRequest request) {
        return service.statuses(request.cameraIds());
    }

    /**
     * 固定摄像头 Ingress 管理操作的目标摄像头标识。
     *
     * @param cameraId 固定摄像头资源 ID
     */
    public record CameraRequest(@Schema(description = "固定摄像头资源 ID", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String cameraId) {
    }
}
