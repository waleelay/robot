package com.robot.mediaserver.file.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.robot.media.common.file.FileType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 申请或恢复机器人分片上传；sourceFileId 用于同一机器人来源文件的幂等复用。 */
public class CreateMultipartFileUploadRequest {
    /**
     * 请求体中的机器人标识；可省略或留空，以必填的 X-Robot-Id 请求头为准，非空时必须与请求头一致。
     */
    @Schema(description = "请求体机器人 ID，可省略；非空时必须与必填的 X-Robot-Id 请求头一致")
    private String robotId;
    /**
     * 设备 ID；未关联时为空。
     */
    @Schema(description = "设备 ID；未关联时为空")
    private String deviceId;
    /**
     * 通用扩展 ID；未关联时为空。
     */
    @Schema(description = "通用扩展 ID；未关联时为空")
    private String extensionId;
    /**
     * 机器人本地来源文件标识；非空时与请求头机器人标识组成幂等键，省略时不按来源复用。
     */
    @Schema(description = "机器人来源文件标识，用于幂等复用")
    private String sourceFileId;

    /**
     * 文件业务类型。
     */
    @NotNull
    @Schema(description = "文件业务类型")
    private FileType fileType;

    /**
     * 原始文件名。
     */
    @NotBlank
    @Schema(description = "原始文件名")
    private String fileName;

    /**
     * 文件媒体类型。
     */
    @NotBlank
    @Schema(description = "文件媒体类型")
    private String contentType;

    /**
     * 文件字节数。
     */
    @Min(1)
    @Schema(description = "文件字节数", requiredMode = Schema.RequiredMode.REQUIRED)
    private long fileSize;

    /**
     * 扩展元数据的原始 JSON 字符串；未设置时为空。
     */
    @Schema(description = "扩展元数据的原始 JSON 字符串；未设置时为空")
    private String metadata;

    /**
     * 读取{@link #robotId}。
     *
     * @return 当前值，含义与约束见{@link #robotId}
     */
    public String getRobotId() { return robotId; }
    /**
     * 更新{@link #robotId}。
     *
     * @param robotId 新值，含义与约束见{@link #robotId}
     */
    public void setRobotId(String robotId) { this.robotId = robotId; }
    /**
     * 读取{@link #deviceId}。
     *
     * @return 当前值，含义与约束见{@link #deviceId}
     */
    public String getDeviceId() { return deviceId; }
    /**
     * 更新{@link #deviceId}。
     *
     * @param deviceId 新值，含义与约束见{@link #deviceId}
     */
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    /**
     * 读取{@link #extensionId}。
     *
     * @return 当前值，含义与约束见{@link #extensionId}
     */
    public String getExtensionId() { return extensionId; }
    /**
     * 更新{@link #extensionId}。
     *
     * @param extensionId 新值，含义与约束见{@link #extensionId}
     */
    public void setExtensionId(String extensionId) { this.extensionId = extensionId; }
    /**
     * 读取{@link #sourceFileId}。
     *
     * @return 当前值，含义与约束见{@link #sourceFileId}
     */
    public String getSourceFileId() { return sourceFileId; }
    /**
     * 更新{@link #sourceFileId}。
     *
     * @param sourceFileId 新值，含义与约束见{@link #sourceFileId}
     */
    public void setSourceFileId(String sourceFileId) { this.sourceFileId = sourceFileId; }
    /**
     * 读取{@link #fileType}。
     *
     * @return 当前值，含义与约束见{@link #fileType}
     */
    public FileType getFileType() { return fileType; }
    /**
     * 更新{@link #fileType}。
     *
     * @param fileType 新值，含义与约束见{@link #fileType}
     */
    public void setFileType(FileType fileType) { this.fileType = fileType; }
    /**
     * 读取{@link #fileName}。
     *
     * @return 当前值，含义与约束见{@link #fileName}
     */
    public String getFileName() { return fileName; }
    /**
     * 更新{@link #fileName}。
     *
     * @param fileName 新值，含义与约束见{@link #fileName}
     */
    public void setFileName(String fileName) { this.fileName = fileName; }
    /**
     * 读取{@link #contentType}。
     *
     * @return 当前值，含义与约束见{@link #contentType}
     */
    public String getContentType() { return contentType; }
    /**
     * 更新{@link #contentType}。
     *
     * @param contentType 新值，含义与约束见{@link #contentType}
     */
    public void setContentType(String contentType) { this.contentType = contentType; }
    /**
     * 读取{@link #fileSize}。
     *
     * @return 当前值，含义与约束见{@link #fileSize}
     */
    public long getFileSize() { return fileSize; }
    /**
     * 更新{@link #fileSize}。
     *
     * @param fileSize 新值，含义与约束见{@link #fileSize}
     */
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }
    /**
     * 读取{@link #metadata}。
     *
     * @return 当前值，含义与约束见{@link #metadata}
     */
    public String getMetadata() { return metadata; }
    /**
     * 更新{@link #metadata}。
     *
     * @param metadata 新值，含义与约束见{@link #metadata}
     */
    public void setMetadata(String metadata) { this.metadata = metadata; }
}
