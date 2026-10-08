package com.robot.mediaserver.fieldcall.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;

import com.robot.media.common.video.CreateFieldCallRequest;
import com.robot.media.common.video.FieldCallResponse;
import com.robot.mediaserver.fieldcall.FieldCallMediaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 现场应用 视频呼叫内网 API（仅供 Control 调用）。
 */
@RestController
@RequestMapping("/internal/media/field-calls")
public class FieldCallController {

    private static final Logger log = LoggerFactory.getLogger(FieldCallController.class);

    private final FieldCallMediaService service;

    /**
     * 初始化 FieldCallController，保存所需依赖及初始运行状态。
     *
     * @param service 现场应用 视频呼叫：创建隔离 Room 并为双方签发 LiveKit Token。
     */
    public FieldCallController(FieldCallMediaService service) {
        this.service = service;
    }

    /**
     * 创建现场呼叫媒体房间并分别签发现场端与指挥端令牌；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param request 请求参数
     * @return 创建现场呼叫媒体房间并分别签发现场端与指挥端令牌的接口响应
     */
    @Operation(
            operationId = "fieldCallController_create",
            summary = "创建现场呼叫媒体房间并分别签发现场端与指挥端令牌",
            description = "创建现场呼叫媒体房间并分别签发现场端与指挥端令牌。受控 Media 服务入口；会话运行状态仍需客户端消息和 LiveKit 轨道事实确认。",
            tags = {"FieldCallController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @PostMapping
    public FieldCallResponse create(@RequestBody CreateFieldCallRequest request) {
        log.info("创建现场呼叫会话 呼叫标识={}, 现场用户标识={}, 中心用户标识={}",
                request.callId(), request.appUserId(), request.centerUserId());
        return service.create(request);
    }
}
