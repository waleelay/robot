package com.robot.media.common.file;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

/** 描述现有文件 HTTP 边界中的表单和错误正文，不替换运行时参数绑定或异常处理。 */
public final class FileHttpSchemas {
    private FileHttpSchemas() {
    }

    /**
     * 简单上传的 multipart/form-data 表单；元数据仍为 JSON 字符串。
     *
     * @param file 文件正文
     * @param fileType 文件类型
     * @param robotId 关联机器人 ID
     * @param deviceId 关联设备 ID
     * @param extensionId 通用扩展 ID
     * @param sourceFileId 来源文件标识
     * @param metadata 扩展元数据 JSON 字符串；source 区分手动媒体所有权规则
     */
    public record UploadForm(
            @Schema(
                    description = "文件正文",
                    type = "string",
                    format = "binary",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            String file,
            @Schema(description = "文件类型", requiredMode = Schema.RequiredMode.REQUIRED) FileType fileType,
            @Schema(description = "关联机器人 ID") String robotId,
            @Schema(description = "关联设备 ID") String deviceId,
            @Schema(description = "通用扩展 ID") String extensionId,
            @Schema(description = "来源文件标识") String sourceFileId,
            @Schema(description = "扩展元数据 JSON 字符串；source 区分手动媒体所有权规则") String metadata) {
    }

    /**
     * Media 业务异常处理器生成的 JSON；details 仅在存在上下文时出现。
     *
     * @param timestamp 上海时区错误时间
     * @param status HTTP 状态码
     * @param code 业务错误码
     * @param message 错误说明
     * @param retryable 是否允许重试
     * @param requestId 请求追踪 ID
     * @param path 请求路径
     * @param details 可选错误上下文；内容由错误码决定
     */
    public record FileBusinessError(
            @Schema(description = "上海时区错误时间", requiredMode = Schema.RequiredMode.REQUIRED) String timestamp,
            @Schema(description = "HTTP 状态码", requiredMode = Schema.RequiredMode.REQUIRED) int status,
            @Schema(description = "业务错误码", requiredMode = Schema.RequiredMode.REQUIRED) String code,
            @Schema(description = "错误说明", requiredMode = Schema.RequiredMode.REQUIRED) String message,
            @Schema(description = "是否允许重试", requiredMode = Schema.RequiredMode.REQUIRED) boolean retryable,
            @Schema(description = "请求追踪 ID", requiredMode = Schema.RequiredMode.REQUIRED) String requestId,
            @Schema(description = "请求路径", requiredMode = Schema.RequiredMode.REQUIRED) String path,
            @Schema(description = "可选错误上下文；内容由错误码决定") Map<String, Object> details) {
    }

    /**
     * Spring Boot 对参数绑定、媒体类型或未处理异常生成的 JSON，附加字段由部署配置决定。
     *
     * @param timestamp 错误时间
     * @param status HTTP 状态码
     * @param error HTTP 错误名称
     * @param path 请求路径
     */
    public record FrameworkError(
            @Schema(description = "错误时间", requiredMode = Schema.RequiredMode.REQUIRED) String timestamp,
            @Schema(description = "HTTP 状态码", requiredMode = Schema.RequiredMode.REQUIRED) int status,
            @Schema(description = "HTTP 错误名称", requiredMode = Schema.RequiredMode.REQUIRED) String error,
            @Schema(description = "请求路径", requiredMode = Schema.RequiredMode.REQUIRED) String path) {
    }

    /**
     * 公开机器人上传路径的来源网络过滤器直接返回的拒绝正文。
     *
     * @param code 网络拒绝错误码
     * @param message 拒绝原因
     */
    public record RobotNetworkError(
            @Schema(
                    description = "网络拒绝错误码",
                    allowableValues = "UNTRUSTED_ROBOT_NETWORK",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            String code,
            @Schema(description = "拒绝原因", requiredMode = Schema.RequiredMode.REQUIRED) String message) {
    }

    /**
     * 扩展绑定的规范调用形态；原有 Map 参数的容错转换不因文档而收紧。
     *
     * @param extensionId 要绑定的通用扩展 ID，不能为空
     * @param fileIds 通用文件 ID，与另两类 ID 合并去重后至少有一项
     * @param videoFileIds 必须为 VIDEO 类型的文件 ID
     * @param pointFileId 单个补充文件 ID
     */
    public record ExtensionBinding(
            @Schema(description = "要绑定的通用扩展 ID，不能为空", requiredMode = Schema.RequiredMode.REQUIRED) String extensionId,
            @Schema(description = "通用文件 ID，与另两类 ID 合并去重后至少有一项") List<String> fileIds,
            @Schema(description = "必须为 VIDEO 类型的文件 ID") List<String> videoFileIds,
            @Schema(description = "单个补充文件 ID") String pointFileId) {
    }
}
