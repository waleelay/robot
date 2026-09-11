package com.robot.mediaserver.fieldcall.api;

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
 * 现场 App 视频呼叫内网 API（仅供 Control 调用）。
 */
@RestController
@RequestMapping("/internal/media/field-calls")
public class FieldCallController {

    private static final Logger log = LoggerFactory.getLogger(FieldCallController.class);

    private final FieldCallMediaService service;

    public FieldCallController(FieldCallMediaService service) {
        this.service = service;
    }

    @PostMapping
    public FieldCallResponse create(@RequestBody CreateFieldCallRequest request) {
        log.info("创建现场呼叫会话 callId={}, appUserId={}, centerUserId={}",
                request.callId(), request.appUserId(), request.centerUserId());
        return service.create(request);
    }
}
