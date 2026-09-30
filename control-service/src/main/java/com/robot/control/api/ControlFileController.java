package com.robot.control.api;

import com.robot.media.common.file.FileHttpSchemas;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.robot.control.mileage.MileageErrorSchemas;
import com.robot.control.auth.CurrentUserResolver;
import com.robot.control.client.ControlMediaServiceClient;
import com.robot.media.common.file.FileBatchDeleteRequest;
import com.robot.media.common.file.FileBatchDeleteResponse;
import com.robot.media.common.file.FileDownloadUrlResponse;
import com.robot.media.common.file.FileListItemResponse;
import com.robot.media.common.file.FileListResponse;
import com.robot.media.common.file.FilePlayUrlResponse;
import com.robot.media.common.file.FileStatus;
import com.robot.media.common.file.FileType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 面向前端的控制侧文件代理接口。
 *
 * @author leelay
 * @date 2026-07-05
 */
@Tag(name = "files", description = "文件上传、访问与处理进度")
@RestController
@RequestMapping("/api/control/files")
public class ControlFileController {

    private final ControlMediaServiceClient mediaServiceClient;
    private final CurrentUserResolver currentUserResolver;

    /**
     * 创建 ControlFileController 实例。
     *
     * @param mediaServiceClient 媒体服务 客户端
     * @param currentUserResolver 当前用户解析器
     */
    public ControlFileController(ControlMediaServiceClient mediaServiceClient, CurrentUserResolver currentUserResolver) {
        this.mediaServiceClient = mediaServiceClient;
        this.currentUserResolver = currentUserResolver;
    }

    /**
     * 代理上传前端提交的简单文件。
     *
     * @param file 上传文件
     * @param fileType 文件类型
     * @param robotId 机器人 ID
     * @param deviceId 设备 ID
     * @param extensionId 通用扩展 ID
     * @param sourceFileId 源文件 ID
     * @param metadata 扩展元数据
     * @param request 请求参数
     * @return 文件信息
     */
    @Operation(
            operationId = "controlUploadFile",
            summary = "上传简单文件",
            description = "使用 multipart/form-data。大小限制由配置决定；视频上传后进入 PROCESSING。用户手动媒体按角色和所有者校验。")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "处理成功；状态、空值和部分失败以响应字段为准",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = FileListItemResponse.class)))
        ,
        @ApiResponse(
                responseCode = "400",
                description = "请求校验或绑定失败",
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class, MileageErrorSchemas.ControlRequestError.class})))
        ,
        @ApiResponse(
                responseCode = "403",
                description = "权限或受控来源网络拒绝",
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class, MileageErrorSchemas.ControlRequestError.class})))
        ,
        @ApiResponse(
                responseCode = "409",
                description = "当前文件或上传状态不允许操作",
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class, MileageErrorSchemas.ControlRequestError.class})))
        ,
        @ApiResponse(
                responseCode = "413",
                description = "文件或代理正文超过限制",
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class, MileageErrorSchemas.ControlRequestError.class})))
        ,
        @ApiResponse(
                responseCode = "503",
                description = "存储或内部凭证配置不可用",
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class, MileageErrorSchemas.ControlRequestError.class})))
        ,
        @ApiResponse(
                responseCode = "500",
                description = "未处理的服务错误",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = FileHttpSchemas.FrameworkError.class)))
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(mediaType = "multipart/form-data", schema = @Schema(implementation = FileHttpSchemas.UploadForm.class)))
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FileListItemResponse uploadSimple(
            @RequestPart("file") MultipartFile file,
            @Parameter(hidden = true) @RequestParam FileType fileType,
            @Parameter(hidden = true) @RequestParam(required = false) String robotId,
            @Parameter(hidden = true) @RequestParam(required = false) String deviceId,
            @Parameter(hidden = true) @RequestParam(required = false) String extensionId,
            @Parameter(hidden = true) @RequestParam(required = false) String sourceFileId,
            @Parameter(hidden = true) @RequestParam(required = false) String metadata,
            HttpServletRequest request) {
        return mediaServiceClient.uploadSimpleFile(
                file,
                fileType,
                robotId,
                deviceId,
                extensionId,
                sourceFileId,
                metadata,
                currentUserResolver.resolve(request));
    }

    /**
     * 按当前用户可见范围分页查询文件。
     *
     * @param robotId 机器人 ID
     * @param deviceId 设备 ID
     * @param extensionId 通用扩展 ID
     * @param fileType 文件类型
     * @param status 文件处理状态
     * @param source 文件来源，对应 metadata.source
     * @param page 从 0 开始的页码，负数由 Media 归一为 0
     * @param size 每页数量，由 Media 限制在 1 至 100
     * @param request 请求参数
     * @return 列表结果
     */
    @Operation(
            operationId = "controlListFiles",
            summary = "分页查询文件",
            description = "按租户及手动媒体所有者过滤；page 从 0 开始，负数归一为 0；size 限制在 1 至 100。source 过滤 metadata.source。")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "处理成功；状态、空值和部分失败以响应字段为准",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = FileListResponse.class)))
        ,
        @ApiResponse(
                responseCode = "400",
                description = "请求校验或绑定失败",
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class, MileageErrorSchemas.ControlRequestError.class})))
        ,
        @ApiResponse(
                responseCode = "500",
                description = "未处理的服务错误",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = FileHttpSchemas.FrameworkError.class)))
    })
    @GetMapping
    public FileListResponse list(
            @Parameter(description = "机器人 ID；分片请求头用于归属校验，列表/上传表单为可选关联") @RequestParam(required = false) String robotId,
            @Parameter(description = "设备 ID") @RequestParam(required = false) String deviceId,
            @Parameter(description = "通用扩展 ID") @RequestParam(required = false) String extensionId,
            @Parameter(description = "文件类型") @RequestParam(required = false) FileType fileType,
            @Parameter(description = "文件处理状态") @RequestParam(required = false) FileStatus status,
            @Parameter(description = "按 metadata.source 过滤") @RequestParam(required = false) String source,
            @Parameter(description = "从 0 开始，负数归一为 0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页条数，归一到 1 至 100") @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        return mediaServiceClient.files(
                robotId,
                deviceId,
                extensionId,
                fileType,
                status,
                source,
                page,
                size,
                currentUserResolver.resolve(request));
    }

    /**
     * 查询文件详情。
     *
     * @param fileId 文件 ID
     * @param request 请求参数
     * @return 文件详情
     */
    @Operation(operationId = "controlGetFile", summary = "查询文件详情", description = "校验当前用户对文件的访问权限；未授权访问的文件可能按未找到返回。")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "处理成功；状态、空值和部分失败以响应字段为准",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = FileListItemResponse.class)))
        ,
        @ApiResponse(
                responseCode = "400",
                description = "请求校验或绑定失败",
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class, MileageErrorSchemas.ControlRequestError.class})))
        ,
        @ApiResponse(
                responseCode = "403",
                description = "权限或受控来源网络拒绝",
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class, MileageErrorSchemas.ControlRequestError.class})))
        ,
        @ApiResponse(
                responseCode = "404",
                description = "文件、上传会话不存在或不可访问",
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class, MileageErrorSchemas.ControlRequestError.class})))
        ,
        @ApiResponse(
                responseCode = "500",
                description = "未处理的服务错误",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = FileHttpSchemas.FrameworkError.class)))
    })
    @GetMapping("/{fileId}")
    public FileListItemResponse detail(@Parameter(description = "文件 ID") @PathVariable String fileId, HttpServletRequest request) {
        return mediaServiceClient.file(fileId, currentUserResolver.resolve(request));
    }

    /**
     * 删除文件。
     *
     * @param fileId 文件 ID
     * @param request 请求参数
     * @return 空响应
     */
    @Operation(operationId = "controlDeleteFile", summary = "删除文件", description = "验证权限后中止有效上传并删除关联对象；已删除文件重复调用保持空响应。")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "处理完成，无正文", content = @Content),
        @ApiResponse(
                responseCode = "400",
                description = "请求校验或绑定失败",
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class, MileageErrorSchemas.ControlRequestError.class})))
        ,
        @ApiResponse(
                responseCode = "403",
                description = "权限或受控来源网络拒绝",
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class, MileageErrorSchemas.ControlRequestError.class})))
        ,
        @ApiResponse(
                responseCode = "404",
                description = "文件、上传会话不存在或不可访问",
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class, MileageErrorSchemas.ControlRequestError.class})))
        ,
        @ApiResponse(
                responseCode = "503",
                description = "存储或内部凭证配置不可用",
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class, MileageErrorSchemas.ControlRequestError.class})))
        ,
        @ApiResponse(
                responseCode = "500",
                description = "未处理的服务错误",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = FileHttpSchemas.FrameworkError.class)))
    })
    @DeleteMapping("/{fileId}")
    public ResponseEntity<Void> delete(@Parameter(description = "文件 ID") @PathVariable String fileId, HttpServletRequest request) {
        mediaServiceClient.deleteFile(fileId, currentUserResolver.resolve(request));
        return ResponseEntity.noContent().build();
    }

    /**
     * 批量删除文件。
     *
     * @param body 批量删除请求
     * @param request 请求参数
     * @return 逐条删除结果
     */
    @Operation(
            operationId = "controlDeleteFiles",
            summary = "批量删除文件",
            description = "一次最多 100 个非空 ID；HTTP 200 中逐条给出成功或失败，允许部分成功。")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "处理成功；状态、空值和部分失败以响应字段为准",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = FileBatchDeleteResponse.class)))
        ,
        @ApiResponse(
                responseCode = "400",
                description = "请求校验或绑定失败",
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class, MileageErrorSchemas.ControlRequestError.class})))
        ,
        @ApiResponse(
                responseCode = "500",
                description = "未处理的服务错误",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = FileHttpSchemas.FrameworkError.class)))
    })
    @DeleteMapping("/batch")
    public FileBatchDeleteResponse deleteBatch(
            @Valid @RequestBody FileBatchDeleteRequest body,
            HttpServletRequest request) {
        return mediaServiceClient.deleteFiles(body, currentUserResolver.resolve(request));
    }

    /**
     * 生成文件下载地址。
     *
     * @param fileId 文件 ID
     * @param inline 是否使用内联展示的响应方式
     * @param request 请求参数
     * @return 下载地址响应
     */
    @Operation(
            operationId = "controlGetFileDownloadUrl",
            summary = "生成下载地址",
            description = "文件必须已就绪；inline 决定 Content-Disposition 展示方式。返回限时签名地址，客户端不应记录完整凭据。")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "处理成功；状态、空值和部分失败以响应字段为准",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = FileDownloadUrlResponse.class)))
        ,
        @ApiResponse(
                responseCode = "400",
                description = "请求校验或绑定失败",
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class, MileageErrorSchemas.ControlRequestError.class})))
        ,
        @ApiResponse(
                responseCode = "403",
                description = "权限或受控来源网络拒绝",
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class, MileageErrorSchemas.ControlRequestError.class})))
        ,
        @ApiResponse(
                responseCode = "404",
                description = "文件、上传会话不存在或不可访问",
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class, MileageErrorSchemas.ControlRequestError.class})))
        ,
        @ApiResponse(
                responseCode = "503",
                description = "存储或内部凭证配置不可用",
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class, MileageErrorSchemas.ControlRequestError.class})))
        ,
        @ApiResponse(
                responseCode = "500",
                description = "未处理的服务错误",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = FileHttpSchemas.FrameworkError.class)))
    })
    @PostMapping("/{fileId}/download-url")
    public FileDownloadUrlResponse downloadUrl(
            @Parameter(description = "文件 ID") @PathVariable String fileId,
            @Parameter(description = "是否在下载时使用内联展示") @RequestParam(defaultValue = "false") boolean inline,
            HttpServletRequest request) {
        return mediaServiceClient.fileDownloadUrl(fileId, currentUserResolver.resolve(request), inline);
    }

    /**
     * 读取文件原始内容。
     *
     * @param fileId 文件 ID
     * @param request 请求参数
     * @return 文件内容响应
     */
    @Operation(
            operationId = "controlGetFileContent",
            summary = "读取文件正文",
            description = "兼容的正文代理，最多读取 32 MiB；媒体类型取自文件记录，大文件使用下载地址。")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "文件正文或 HLS 资源",
                content = {@Content(mediaType = "*/*", schema = @Schema(type = "string", format = "binary"))})
        ,
        @ApiResponse(
                responseCode = "400",
                description = "请求校验或绑定失败",
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class, MileageErrorSchemas.ControlRequestError.class})))
        ,
        @ApiResponse(
                responseCode = "403",
                description = "权限或受控来源网络拒绝",
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class, MileageErrorSchemas.ControlRequestError.class})))
        ,
        @ApiResponse(
                responseCode = "404",
                description = "文件、上传会话不存在或不可访问",
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class, MileageErrorSchemas.ControlRequestError.class})))
        ,
        @ApiResponse(
                responseCode = "413",
                description = "文件或代理正文超过限制",
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class, MileageErrorSchemas.ControlRequestError.class})))
        ,
        @ApiResponse(
                responseCode = "503",
                description = "存储或内部凭证配置不可用",
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class, MileageErrorSchemas.ControlRequestError.class})))
        ,
        @ApiResponse(
                responseCode = "500",
                description = "未处理的服务错误",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = FileHttpSchemas.FrameworkError.class)))
    })
    @GetMapping("/{fileId}/content")
    public ResponseEntity<byte[]> content(@Parameter(description = "文件 ID") @PathVariable String fileId, HttpServletRequest request) {
        return mediaServiceClient.fileContent(fileId, currentUserResolver.resolve(request));
    }

    /**
     * 生成文件播放地址。
     *
     * @param fileId 文件 ID
     * @param request 请求参数
     * @return 播放地址响应
     */
    @Operation(
            operationId = "controlGetFilePlayUrl",
            summary = "生成 HLS 播放地址",
            description = "只支持已就绪的视频；返回带播放 token 的 HLS 清单地址。Control 将 Media 地址前缀改写为 /api/control/files。")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "处理成功；状态、空值和部分失败以响应字段为准",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = FilePlayUrlResponse.class)))
        ,
        @ApiResponse(
                responseCode = "400",
                description = "请求校验或绑定失败",
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class, MileageErrorSchemas.ControlRequestError.class})))
        ,
        @ApiResponse(
                responseCode = "403",
                description = "权限或受控来源网络拒绝",
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class, MileageErrorSchemas.ControlRequestError.class})))
        ,
        @ApiResponse(
                responseCode = "404",
                description = "文件、上传会话不存在或不可访问",
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class, MileageErrorSchemas.ControlRequestError.class})))
        ,
        @ApiResponse(
                responseCode = "500",
                description = "未处理的服务错误",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = FileHttpSchemas.FrameworkError.class)))
    })
    @PostMapping("/{fileId}/play-url")
    public FilePlayUrlResponse playUrl(@Parameter(description = "文件 ID") @PathVariable String fileId, HttpServletRequest request) {
        FilePlayUrlResponse response = mediaServiceClient.filePlayUrl(fileId, currentUserResolver.resolve(request));
        return new FilePlayUrlResponse(
                response.fileId(),
                response.format(),
                response.contentType(),
                toControlPlayUrl(response.playUrl()),
                response.expiresAt());
    }

    /**
     * 代理读取 HLS 播放资源。
     *
     * @param fileId 文件 ID
     * @param objectName 对象名称
     * @param token 访问令牌
     * @return HLS 资源响应
     */
    @Operation(
            operationId = "controlGetFileHlsAsset",
            summary = "读取 HLS 清单或分片",
            description = "通过查询参数 token 验证文件和有效期，不调用用户身份解析器。清单内相对地址携带播放 token；每个对象最多 32 MiB。")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "文件正文或 HLS 资源",
                content = {@Content(mediaType = "application/vnd.apple.mpegurl", schema = @Schema(type = "string", format = "binary")), @Content(mediaType = "video/mp4", schema = @Schema(type = "string", format = "binary")), @Content(mediaType = "video/mp2t", schema = @Schema(type = "string", format = "binary")), @Content(mediaType = "application/octet-stream", schema = @Schema(type = "string", format = "binary"))})
        ,
        @ApiResponse(
                responseCode = "400",
                description = "请求校验或绑定失败",
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class, MileageErrorSchemas.ControlRequestError.class})))
        ,
        @ApiResponse(
                responseCode = "401",
                description = "机器人标识或播放/内部凭证无效",
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class, MileageErrorSchemas.ControlRequestError.class})))
        ,
        @ApiResponse(
                responseCode = "404",
                description = "文件、上传会话不存在或不可访问",
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class, MileageErrorSchemas.ControlRequestError.class})))
        ,
        @ApiResponse(
                responseCode = "413",
                description = "文件或代理正文超过限制",
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class, MileageErrorSchemas.ControlRequestError.class})))
        ,
        @ApiResponse(
                responseCode = "503",
                description = "存储或内部凭证配置不可用",
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class, MileageErrorSchemas.ControlRequestError.class})))
        ,
        @ApiResponse(
                responseCode = "500",
                description = "未处理的服务错误",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = FileHttpSchemas.FrameworkError.class)))
    })
    @GetMapping("/{fileId}/hls/{objectName}")
    public ResponseEntity<byte[]> hls(
            @Parameter(description = "文件 ID") @PathVariable String fileId,
            @Parameter(description = "HLS 清单或分片对象名") @PathVariable String objectName,
            @Parameter(description = "限时文件播放 token") @RequestParam String token) {
        byte[] body = mediaServiceClient.fileHlsAsset(fileId, objectName, token);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(hlsContentType(objectName)))
                .body(body);
    }

    /**
     * 根据 HLS 对象名推导响应 Content-Type。
     *
     * @param objectName 对象名称
     * @return Content-Type 字符串
     */
    private String hlsContentType(String objectName) {
        if (objectName.endsWith(".m3u8")) {
            return "application/vnd.apple.mpegurl";
        }
        if (objectName.endsWith(".m4s") || objectName.endsWith(".mp4")) {
            return "video/mp4";
        }
        if (objectName.endsWith(".ts")) {
            return "video/mp2t";
        }
        return "application/octet-stream";
    }

    private String toControlPlayUrl(String playUrl) {
        if (playUrl == null || playUrl.isBlank()) {
            return playUrl;
        }
        return playUrl.replaceFirst("/api/media/files/", "/api/control/files/");
    }
}
