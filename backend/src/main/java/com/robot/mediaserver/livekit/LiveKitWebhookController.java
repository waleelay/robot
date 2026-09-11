package com.robot.mediaserver.livekit;

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

    public LiveKitWebhookController(LiveKitWebhookService webhookService) {
        this.webhookService = webhookService;
    }

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
