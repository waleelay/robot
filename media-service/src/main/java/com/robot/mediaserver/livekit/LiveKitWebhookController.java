package com.robot.mediaserver.livekit;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;

import com.robot.mediaserver.livekit.LiveKitWebhookService.InvalidWebhookAuthenticationException;
import com.robot.mediaserver.livekit.LiveKitWebhookService.InvalidWebhookPayloadException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** LiveKit Server 内部 Webhook 入口。 */
@RestController
@RequestMapping("/internal/media/livekit")
public class LiveKitWebhookController {

    private final LiveKitWebhookService webhookService;

    /**
     * 初始化 LiveKitWebhookController，保存所需依赖及初始运行状态。
     *
     * @param webhookService 校验并消费 LiveKit Webhook；具体状态变更始终通过 Room API 当前事实完成。
     */
    public LiveKitWebhookController(LiveKitWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    /**
     * 接收经过 LiveKit 签名验证的房间和 Egress 事件；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param body 请求体
     * @param authorization 调用方提供的 Authorization 头；凭据不得写入日志
     * @return 接收经过 LiveKit 签名验证的房间和 Egress 事件的接口响应
     */
    @Operation(
            operationId = "liveKitWebhookController_webhook",
            summary = "接收经过 LiveKit 签名验证的房间和 Egress 事件",
            description = "接收经过 LiveKit 签名验证的房间和 Egress 事件。受控 Media 服务入口；会话运行状态仍需客户端消息和 LiveKit 轨道事实确认。",
            tags = {"LiveKitWebhookController"})
    @ApiResponse(responseCode = "204", description = "处理完成，无响应正文", content = @Content)
    @PostMapping(value = "/webhook", consumes = {"application/webhook+json", "application/json"})
    public ResponseEntity<Void> webhook(
            @RequestBody byte[] body,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        try {
            webhookService.receive(body, authorization);
            return ResponseEntity.noContent().build();
        } catch (InvalidWebhookAuthenticationException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } catch (InvalidWebhookPayloadException exception) {
            return ResponseEntity.badRequest().build();
        }
    }
}
