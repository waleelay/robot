package com.robot.control.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;

import com.robot.control.fixedcamera.FixedCameraCatalogLeaseRequest;
import com.robot.control.fixedcamera.FixedCameraCatalogLeaseService;
import com.robot.control.fixedcamera.FixedCameraCatalogSnapshot;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** BFF 与 Control 之间的固定摄像头目录租约入口，不向浏览器暴露。 */
@RestController
@RequestMapping("/internal/control/fixed-camera-catalog-leases")
public class InternalFixedCameraCatalogController {

    private final FixedCameraCatalogLeaseService leaseService;

    /**
     * 允许维护目录租约的 BFF 调用方标识，入口只部署在受控内网。
     */
    @Value("${control.fixed-camera-catalog.trusted-caller:bigscreen-bff}")
    private String trustedCaller = "bigscreen-bff";

    /**
     * 初始化 InternalFixedCameraCatalogController，保存所需依赖及初始运行状态。
     *
     * @param leaseService 管理 BFF 下发的用户授权目录租约，并只向 Gateway 下发当前有效的合并快照。
     */
    public InternalFixedCameraCatalogController(FixedCameraCatalogLeaseService leaseService) {
        this.leaseService = leaseService;
    }

    /**
     * 写入或续期可信 BFF 提交的摄像头目录租约；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param request 请求参数
     * @param servletRequest HTTP 请求
     * @return 写入或续期可信 BFF 提交的摄像头目录租约的接口响应
     */
    @Operation(
            operationId = "internalFixedCameraCatalogController_upsert",
            summary = "写入或续期可信 BFF 提交的摄像头目录租约",
            description = "写入或续期可信 BFF 提交的摄像头目录租约。受控服务入口；使用用户上下文的方法依赖可信上游 Header，编排成功不等于设备已完成动作。",
            tags = {"InternalFixedCameraCatalogController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @PutMapping
    public FixedCameraCatalogSnapshot upsert(
            @RequestBody FixedCameraCatalogLeaseRequest request,
            HttpServletRequest servletRequest) {
        String caller = servletRequest.getHeader("X-Internal-Caller");
        if (caller == null || !caller.equals(trustedCaller)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "固定摄像头目录租约调用方不受信任");
        }
        return leaseService.upsert(request);
    }

    /**
     * BFF 最后一个同身份会话关闭时主动撤销目录租约。
     *
     * @param leaseId 目录租约 ID
     * @param servletRequest HTTP 请求
     */
    @Operation(
            operationId = "internalFixedCameraCatalogController_release",
            summary = "撤销指定摄像头目录租约",
            description = "撤销指定摄像头目录租约。受控服务入口；使用用户上下文的方法依赖可信上游 Header，编排成功不等于设备已完成动作。",
            tags = {"InternalFixedCameraCatalogController"})
    @ApiResponse(responseCode = "200", description = "处理完成，无响应正文", content = @Content)
    @DeleteMapping("/{leaseId}")
    public void release(@PathVariable String leaseId, HttpServletRequest servletRequest) {
        String caller = servletRequest.getHeader("X-Internal-Caller");
        if (caller == null || !caller.equals(trustedCaller)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "固定摄像头目录租约调用方不受信任");
        }
        leaseService.release(leaseId);
    }
}
