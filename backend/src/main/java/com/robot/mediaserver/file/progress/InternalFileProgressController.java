package com.robot.mediaserver.file.progress;

import com.robot.media.common.file.FileHttpSchemas;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 提供内网批量进度查询和 MinIO 回调；只有回调入口校验配置的内部凭证。 */
@Tag(name = "files", description = "文件上传、访问与处理进度")
@RestController
@RequestMapping("/internal/media")
public class InternalFileProgressController {

    private final FileUploadProgressService progressService;
    private final FileProgressAuthentication authentication;

    /**
     * 初始化 InternalFileProgressController，保存所需依赖及初始运行状态。
     * @param progressService 合并 MinIO 分片通知与 Redis 进度快照，缓存缺失时通过有界回源重建状态。
     * @param authentication MinIO 回调令牌校验器
     */
    public InternalFileProgressController(
            FileUploadProgressService progressService,
            FileProgressAuthentication authentication) {
        this.progressService = progressService;
        this.authentication = authentication;
    }

    /**
     * 批量查询上传进度；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param request 请求参数
     * @return 批量查询上传进度的接口响应
     */
    @Operation(
            operationId = "mediaQueryFileUploadProgress",
            summary = "批量查询上传进度",
            description = "仅受控内网调用，本入口没有 Bearer 凭证校验。最多 500 个 ID；去空白和重复后查询，缺失 ID 单独返回。百分比为 100 不代表后处理就绪。")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "处理成功；状态、空值和部分失败以响应字段为准",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = FileUploadProgressQueryResponse.class)))
        ,
        @ApiResponse(
                responseCode = "400",
                description = "请求校验或绑定失败",
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class})))
        ,
        @ApiResponse(
                responseCode = "500",
                description = "未处理的服务错误",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = FileHttpSchemas.FrameworkError.class)))
    })
    @PostMapping("/files/upload-progress-queries")
    public FileUploadProgressQueryResponse query(@Valid @RequestBody FileUploadProgressQueryRequest request) {
        return progressService.query(request.fileIds());
    }

    /**
     * 接收 MinIO 分片事件；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param authorization 调用方提供的 Authorization 头；凭据不得写入日志
     * @param payload 消息载荷
     * @return 接收 MinIO 分片事件的接口响应
     */
    @Operation(
            operationId = "mediaReceiveFilePartEvent",
            summary = "接收 MinIO 分片事件",
            description = "校验配置的内部 Bearer 凭证；识别 s3:ObjectCreated: 事件与分片对象键，忽略无关对象和未知会话；重复分片由进度服务去重。")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "处理完成，无正文", content = @Content),
        @ApiResponse(
                responseCode = "400",
                description = "请求校验或绑定失败",
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class})))
        ,
        @ApiResponse(
                responseCode = "401",
                description = "机器人标识或播放/内部凭证无效",
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class})))
        ,
        @ApiResponse(
                responseCode = "503",
                description = "存储或内部凭证配置不可用",
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class})))
        ,
        @ApiResponse(
                responseCode = "500",
                description = "未处理的服务错误",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = FileHttpSchemas.FrameworkError.class)))
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(schema = @Schema(implementation = MinioPartEvent.class)))
    @PostMapping("/file-upload-events/minio")
    public ResponseEntity<Void> minioEvent(
            @Parameter(description = "MinIO 回调内部 Bearer 凭证；不是用户 JWT") @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody JsonNode payload) {
        authentication.requireWebhookToken(authorization);
        progressService.acceptMinioEvent(payload);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
