package com.robot.mediaserver.tts.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;

import com.robot.mediaserver.tts.service.TtsAudioService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 接收机器人语音合成请求并委托音频服务处理。 */
@RestController
@RequestMapping("/api/media/tts")
public class RobotTtsController {

    private final TtsAudioService service;

    /**
     * 初始化 RobotTtsController，保存所需依赖及初始运行状态。
     *
     * @param service 调用语音引擎、复用缓存文件，并向媒体通道发布生成音频。
     */
    public RobotTtsController(TtsAudioService service) {
        this.service = service;
    }

    /**
     * 生成或复用机器人语音文件并返回文件正文；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param robotId 机器人 ID
     * @param text 待合成的语音文本
     * @return 生成或复用机器人语音文件并返回文件正文的接口响应
     */
    @Operation(
            operationId = "robotTtsController_generateFile",
            summary = "生成或复用机器人语音文件并返回文件正文",
            description = "生成或复用机器人语音文件并返回文件正文。X-Robot-Id 标识机器人，依赖受控网络；同一文本复用缓存，生成成功不表示机器人已播放。",
            tags = {"RobotTtsController"})
    @ApiResponse(
            responseCode = "200",
            description = "响应正文；下载接口保留 Content-Type 与 Content-Disposition",
            content = @Content(mediaType = "*/*", schema = @Schema(type = "string", format = "binary")))
    @GetMapping("/generate-file")
    public ResponseEntity<FileSystemResource> generateFile(
            @RequestHeader("X-Robot-Id") String robotId,
            @RequestParam String text) {
        return service.generateAndReturnFile(robotId, text);
    }

    /**
     * 生成语音并通过媒体 WebSocket 发布音频及格式信息；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param robotId 机器人 ID
     * @param text 待合成的语音文本
     */
    @Operation(
            operationId = "robotTtsController_generateAndPublishToFrontend",
            summary = "生成语音并通过媒体 WebSocket 发布音频及格式信息",
            description = "生成语音并通过媒体 WebSocket 发布音频及格式信息。X-Robot-Id 标识机器人，依赖受控网络；同一文本复用缓存，生成成功不表示机器人已播放。",
            tags = {"RobotTtsController"})
    @ApiResponse(responseCode = "200", description = "处理完成，无响应正文", content = @Content)
    @GetMapping("/generate-and-play")
    public void generateAndPublishToFrontend(
            @RequestHeader("X-Robot-Id") String robotId,
            @RequestParam String text) {
        service.generateAndPublishToFrontend(robotId, text);
    }
}
