package com.robot.mediaserver.file.api;

import com.robot.media.common.file.FileHttpSchemas;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.robot.mediaserver.auth.CurrentUserResolver;
import com.robot.mediaserver.file.dto.CreateMultipartFileUploadRequest;
import com.robot.media.common.file.FileBatchDeleteRequest;
import com.robot.media.common.file.FileBatchDeleteResponse;
import com.robot.media.common.file.FileDownloadUrlResponse;
import com.robot.media.common.file.FileListItemResponse;
import com.robot.media.common.file.FileListResponse;
import com.robot.mediaserver.file.dto.FilePartUrlsRequest;
import com.robot.mediaserver.file.dto.FilePartUrlsResponse;
import com.robot.media.common.file.FilePlayUrlResponse;
import com.robot.mediaserver.file.dto.FileStatusResponse;
import com.robot.mediaserver.file.dto.FileUploadResponse;
import com.robot.media.common.file.FileStatus;
import com.robot.media.common.file.FileType;
import com.robot.mediaserver.file.service.FileService;
import com.robot.mediaserver.file.service.FileMultipartCompletionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 提供 Media 文件上传、查询、授权地址及正文读取；机器人分片接口与用户文件接口采用不同的身份边界。 */
@Tag(name = "files", description = "文件上传、访问与处理进度")
@RestController
@RequestMapping({"/internal/media/files", "/api/media/files"})
public class FileController {

    private final FileService service;
    private final FileMultipartCompletionService multipartCompletionService;
    private final CurrentUserResolver currentUserResolver;

    /**
     * 初始化 FileController，保存所需依赖及初始运行状态。
     *
     * @param service 管理文件元数据、上传会话、所有权与播放授权，协调存储及视频处理状态。
     * @param multipartCompletionService 使用短数据库事务协调分片合并，避免 拼接大文件期间长期占用数据库连接和行锁。
     * @param currentUserResolver 当前用户解析器
     */
    public FileController(
            FileService service,
            FileMultipartCompletionService multipartCompletionService,
            CurrentUserResolver currentUserResolver) {
        this.service = service;
        this.multipartCompletionService = multipartCompletionService;
        this.currentUserResolver = currentUserResolver;
    }

    /**
     * 上传简单文件；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param file 上传文件
     * @param fileType 文件类型
     * @param robotId 机器人 ID
     * @param deviceId 设备 ID
     * @param extensionId 通用扩展 ID
     * @param sourceFileId 源文件 ID
     * @param metadata 扩展元数据
     * @param request 请求参数
     * @return 上传简单文件的接口响应
     */
    @Operation(
            operationId = "mediaUploadFile",
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
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class})))
        ,
        @ApiResponse(
                responseCode = "403",
                description = "权限或受控来源网络拒绝",
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class})))
        ,
        @ApiResponse(
                responseCode = "409",
                description = "当前文件或上传状态不允许操作",
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class})))
        ,
        @ApiResponse(
                responseCode = "413",
                description = "文件或代理正文超过限制",
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
        return service.uploadSimple(
                currentUserResolver.resolve(request),
                file,
                fileType,
                robotId,
                deviceId,
                extensionId,
                sourceFileId,
                metadata);
    }

    /**
     * 申请或恢复分片上传；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param robotId 机器人 ID
     * @param request 请求参数
     * @return 申请或恢复分片上传的接口响应
     */
    @Operation(
            operationId = "mediaCreateFileMultipartUpload",
            summary = "申请或恢复分片上传",
            description = "X-Robot-Id 为受控网络中的机器人标识；与请求体 robotId 不一致时拒绝。同一 robotId/sourceFileId 复用会话，来源已完成时不创建新会话。分片正文由返回地址直传 MinIO。")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "处理成功；状态、空值和部分失败以响应字段为准",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = FileUploadResponse.class)))
        ,
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
                responseCode = "403",
                description = "权限或受控来源网络拒绝",
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class, FileHttpSchemas.RobotNetworkError.class})))
        ,
        @ApiResponse(
                responseCode = "409",
                description = "当前文件或上传状态不允许操作",
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class})))
        ,
        @ApiResponse(
                responseCode = "413",
                description = "文件或代理正文超过限制",
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class})))
        ,
        @ApiResponse(
                responseCode = "429",
                description = "上传会话配额达到上限",
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
    @PostMapping("/multipart-uploads")
    public FileUploadResponse createMultipart(
            @Parameter(description = "机器人 ID；分片请求头用于归属校验，列表/上传表单为可选关联") @RequestHeader("X-Robot-Id") String robotId,
            @Valid @RequestBody CreateMultipartFileUploadRequest request) {
        return service.createOrResumeMultipart(robotId, request);
    }

    /**
     * 签发分片上传地址；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param robotId 机器人 ID
     * @param uploadId 平台上传会话 ID
     * @param request 请求参数
     * @return 签发分片上传地址的接口响应
     */
    @Operation(
            operationId = "mediaSignFilePartUrls",
            summary = "签发分片上传地址",
            description = "校验上传会话归属和有效期；分片编号从 1 开始，必须在会话范围内，数量上限由配置决定；重复编号去重。")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "处理成功；状态、空值和部分失败以响应字段为准",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = FilePartUrlsResponse.class)))
        ,
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
                responseCode = "403",
                description = "权限或受控来源网络拒绝",
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class, FileHttpSchemas.RobotNetworkError.class})))
        ,
        @ApiResponse(
                responseCode = "404",
                description = "文件、上传会话不存在或不可访问",
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class})))
        ,
        @ApiResponse(
                responseCode = "409",
                description = "当前文件或上传状态不允许操作",
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
    @PostMapping("/multipart-uploads/{uploadId}/part-urls")
    public FilePartUrlsResponse partUrls(
            @Parameter(description = "机器人 ID；分片请求头用于归属校验，列表/上传表单为可选关联") @RequestHeader("X-Robot-Id") String robotId,
            @Parameter(description = "平台上传会话 ID") @PathVariable String uploadId,
            @Valid @RequestBody FilePartUrlsRequest request) {
        return service.partUrls(robotId, uploadId, request.getPartNumbers());
    }

    /**
     * 完成分片上传；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param robotId 机器人 ID
     * @param uploadId 平台上传会话 ID
     * @return 完成分片上传的接口响应
     */
    @Operation(
            operationId = "mediaCompleteFileUpload",
            summary = "完成分片上传",
            description = "校验分片连续性及总字节数后合并；重复完成可返回已有状态，合并中的调用返回 409 UPLOAD_COMPLETION_IN_PROGRESS。成功合并不等于视频后处理已完成。")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "处理成功；状态、空值和部分失败以响应字段为准",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = FileStatusResponse.class)))
        ,
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
                responseCode = "403",
                description = "权限或受控来源网络拒绝",
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class, FileHttpSchemas.RobotNetworkError.class})))
        ,
        @ApiResponse(
                responseCode = "404",
                description = "文件、上传会话不存在或不可访问",
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class})))
        ,
        @ApiResponse(
                responseCode = "409",
                description = "当前文件或上传状态不允许操作",
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
    @PostMapping("/multipart-uploads/{uploadId}/complete")
    public FileStatusResponse complete(
            @Parameter(description = "机器人 ID；分片请求头用于归属校验，列表/上传表单为可选关联") @RequestHeader("X-Robot-Id") String robotId,
            @Parameter(description = "平台上传会话 ID") @PathVariable String uploadId) {
        return multipartCompletionService.complete(robotId, uploadId);
    }

    /**
     * 查询机器人文件状态；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param robotId 机器人 ID
     * @param fileId 文件 ID
     * @return 查询机器人文件状态的接口响应
     */
    @Operation(
            operationId = "mediaGetRobotFileStatus",
            summary = "查询机器人文件状态",
            description = "校验 X-Robot-Id 与文件归属；ready 表示后处理也已完成。")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "处理成功；状态、空值和部分失败以响应字段为准",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = FileStatusResponse.class)))
        ,
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
                responseCode = "403",
                description = "权限或受控来源网络拒绝",
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class, FileHttpSchemas.RobotNetworkError.class})))
        ,
        @ApiResponse(
                responseCode = "404",
                description = "文件、上传会话不存在或不可访问",
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class})))
        ,
        @ApiResponse(
                responseCode = "500",
                description = "未处理的服务错误",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = FileHttpSchemas.FrameworkError.class)))
    })
    @GetMapping("/{fileId}/status")
    public FileStatusResponse status(
            @Parameter(description = "机器人 ID；分片请求头用于归属校验，列表/上传表单为可选关联") @RequestHeader("X-Robot-Id") String robotId,
            @Parameter(description = "文件 ID") @PathVariable String fileId) {
        return service.fileStatus(robotId, fileId);
    }

    /**
     * 分页查询文件；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param robotId 机器人 ID
     * @param deviceId 设备 ID
     * @param extensionId 通用扩展 ID
     * @param fileType 文件类型
     * @param status 当前业务状态，取值遵循所属模型的状态协议
     * @param source 文件来源，对应 metadata.source
     * @param page 页码
     * @param size 分页大小
     * @param request 请求参数
     * @return 分页查询文件的接口响应
     */
    @Operation(
            operationId = "mediaListFiles",
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
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class})))
        ,
        @ApiResponse(
                responseCode = "404",
                description = "当前异常处理器将数字或枚举转换的 IllegalArgumentException 映射为 NOT_FOUND",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = FileHttpSchemas.FileBusinessError.class)))
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
        return service.list(currentUserResolver.resolve(request), robotId, deviceId, extensionId, fileType, status, source, page, size);
    }

    /**
     * 绑定通用扩展 ID；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param body 请求体
     * @param request 请求参数
     * @return 绑定通用扩展 ID的接口响应
     */
    @Operation(
            operationId = "mediaBindFileExtension",
            summary = "绑定通用扩展 ID",
            description = "合并 fileIds、videoFileIds、pointFileId 后去重；先校验全部文件权限与类型，再绑定 extensionId。规范请求使用字符串 ID；保留既有 Map 容错。")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "处理完成，无正文", content = @Content),
        @ApiResponse(
                responseCode = "400",
                description = "请求校验或绑定失败",
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class})))
        ,
        @ApiResponse(
                responseCode = "403",
                description = "权限或受控来源网络拒绝",
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class})))
        ,
        @ApiResponse(
                responseCode = "404",
                description = "文件、上传会话不存在或不可访问",
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class})))
        ,
        @ApiResponse(
                responseCode = "500",
                description = "未处理的服务错误",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = FileHttpSchemas.FrameworkError.class)))
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(schema = @Schema(implementation = FileHttpSchemas.ExtensionBinding.class)))
    @PostMapping("/extension-binding")
    public ResponseEntity<Void> bindExtension(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        service.bindExtension(currentUserResolver.resolve(request), body);
        return ResponseEntity.noContent().build();
    }

    /**
     * 查询文件详情；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param fileId 文件 ID
     * @param request 请求参数
     * @return 查询文件详情的接口响应
     */
    @Operation(operationId = "mediaGetFile", summary = "查询文件详情", description = "校验当前用户对文件的访问权限；未授权访问的文件可能按未找到返回。")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "处理成功；状态、空值和部分失败以响应字段为准",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = FileListItemResponse.class)))
        ,
        @ApiResponse(
                responseCode = "403",
                description = "权限或受控来源网络拒绝",
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class})))
        ,
        @ApiResponse(
                responseCode = "404",
                description = "文件、上传会话不存在或不可访问",
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class})))
        ,
        @ApiResponse(
                responseCode = "500",
                description = "未处理的服务错误",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = FileHttpSchemas.FrameworkError.class)))
    })
    @GetMapping("/{fileId}")
    public FileListItemResponse detail(@Parameter(description = "文件 ID") @PathVariable String fileId, HttpServletRequest request) {
        return service.detail(currentUserResolver.resolve(request), fileId);
    }

    /**
     * 删除文件；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param fileId 文件 ID
     * @param request 请求参数
     * @return 删除文件的接口响应
     */
    @Operation(operationId = "mediaDeleteFile", summary = "删除文件", description = "验证权限后中止有效上传并删除关联对象；已删除文件重复调用保持空响应。")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "处理完成，无正文", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "权限或受控来源网络拒绝",
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class})))
        ,
        @ApiResponse(
                responseCode = "404",
                description = "文件、上传会话不存在或不可访问",
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
    @DeleteMapping("/{fileId}")
    public ResponseEntity<Void> delete(@Parameter(description = "文件 ID") @PathVariable String fileId, HttpServletRequest request) {
        service.delete(currentUserResolver.resolve(request), fileId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 批量删除文件；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param body 请求体
     * @param request 请求参数
     * @return 批量删除文件的接口响应
     */
    @Operation(
            operationId = "mediaDeleteFiles",
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
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class})))
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
        return service.deleteBatch(currentUserResolver.resolve(request), body.fileIds());
    }

    /**
     * 生成下载地址；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param fileId 文件 ID
     * @param inline 是否使用内联展示的响应方式
     * @param request 请求参数
     * @return 生成下载地址的接口响应
     */
    @Operation(
            operationId = "mediaGetFileDownloadUrl",
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
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class})))
        ,
        @ApiResponse(
                responseCode = "403",
                description = "权限或受控来源网络拒绝",
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class})))
        ,
        @ApiResponse(
                responseCode = "404",
                description = "文件、上传会话不存在或不可访问",
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
    @PostMapping("/{fileId}/download-url")
    public FileDownloadUrlResponse downloadUrl(
            @Parameter(description = "文件 ID") @PathVariable String fileId,
            @Parameter(description = "是否在下载时使用内联展示") @RequestParam(defaultValue = "false") boolean inline,
            HttpServletRequest request) {
        return service.downloadUrl(currentUserResolver.resolve(request), fileId, inline);
    }

    /**
     * 读取文件正文；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param fileId 文件 ID
     * @param request 请求参数
     * @return 读取文件正文的接口响应
     */
    @Operation(
            operationId = "mediaGetFileContent",
            summary = "读取文件正文",
            description = "兼容的正文代理，最多读取 32 MiB；媒体类型取自文件记录，大文件使用下载地址。")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "文件正文或 HLS 资源",
                content = {@Content(mediaType = "*/*", schema = @Schema(type = "string", format = "binary"))})
        ,
        @ApiResponse(
                responseCode = "403",
                description = "权限或受控来源网络拒绝",
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class})))
        ,
        @ApiResponse(
                responseCode = "404",
                description = "文件、上传会话不存在或不可访问",
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class})))
        ,
        @ApiResponse(
                responseCode = "413",
                description = "文件或代理正文超过限制",
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
    @GetMapping("/{fileId}/content")
    public ResponseEntity<byte[]> content(@Parameter(description = "文件 ID") @PathVariable String fileId, HttpServletRequest request) {
        FileService.PlaybackAsset asset = service.content(currentUserResolver.resolve(request), fileId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(asset.contentType()))
                .body(asset.bytes());
    }

    /**
     * 生成 HLS 播放地址；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param fileId 文件 ID
     * @param request 请求参数
     * @return 生成 HLS 播放地址的接口响应
     */
    @Operation(
            operationId = "mediaGetFilePlayUrl",
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
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class})))
        ,
        @ApiResponse(
                responseCode = "403",
                description = "权限或受控来源网络拒绝",
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class})))
        ,
        @ApiResponse(
                responseCode = "404",
                description = "文件、上传会话不存在或不可访问",
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class})))
        ,
        @ApiResponse(
                responseCode = "500",
                description = "未处理的服务错误",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = FileHttpSchemas.FrameworkError.class)))
    })
    @PostMapping("/{fileId}/play-url")
    public FilePlayUrlResponse playUrl(@Parameter(description = "文件 ID") @PathVariable String fileId, HttpServletRequest request) {
        return service.playUrl(currentUserResolver.resolve(request), fileId);
    }

    /**
     * 读取 HLS 清单或分片；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param fileId 文件 ID
     * @param objectName 对象名称
     * @param token 访问令牌
     * @return 读取 HLS 清单或分片的接口响应
     */
    @Operation(
            operationId = "mediaGetFileHlsAsset",
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
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class})))
        ,
        @ApiResponse(
                responseCode = "401",
                description = "机器人标识或播放/内部凭证无效",
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class})))
        ,
        @ApiResponse(
                responseCode = "404",
                description = "文件、上传会话不存在或不可访问",
                content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {FileHttpSchemas.FileBusinessError.class, FileHttpSchemas.FrameworkError.class})))
        ,
        @ApiResponse(
                responseCode = "413",
                description = "文件或代理正文超过限制",
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
    @GetMapping("/{fileId}/hls/{objectName}")
    public ResponseEntity<byte[]> hls(
            @Parameter(description = "文件 ID") @PathVariable String fileId,
            @Parameter(description = "HLS 清单或分片对象名") @PathVariable String objectName,
            @Parameter(description = "限时文件播放 token") @RequestParam String token) {
        FileService.PlaybackAsset asset = service.playbackAsset(fileId, objectName, token);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(asset.contentType()))
                .body(asset.bytes());
    }
}
