package com.robot.media.common.file;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/**
 * 文件列表单项响应。
 *
 * @author leelay
 * @date 2026-07-05
 *
 * @param fileId 文件 ID
 * @param robotId 机器人 ID
 * @param deviceId 设备 ID
 * @param extensionId 通用扩展 ID
 * @param fileType 文件类型
 * @param fileName 文件名
 * @param contentType 内容类型
 * @param fileSize 文件大小
 * @param durationSeconds 时长秒数
 * @param startedAt 开始时间
 * @param endedAt 结束时间
 * @param width 宽度
 * @param height 高度
 * @param status 状态
 * @param videoStatus 视频状态
 * @param errorCode 错误码
 * @param uploadedAt 上传时间
 * @param createdAt 创建时间
 * @param metadata 扩展元数据
 * @param elapsedSeconds 当前录像已持续秒数
 */
public record FileListItemResponse(
        @Schema(description = "文件 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        String fileId,
        @Schema(description = "机器人 ID；未关联时为空", requiredMode = Schema.RequiredMode.REQUIRED, types = {"string", "null"})
        String robotId,
        @Schema(description = "设备 ID；未关联时为空", requiredMode = Schema.RequiredMode.REQUIRED, types = {"string", "null"})
        String deviceId,
        @Schema(description = "通用扩展 ID；未关联时为空", requiredMode = Schema.RequiredMode.REQUIRED, types = {"string", "null"})
        String extensionId,
        @Schema(description = "文件业务类型", requiredMode = Schema.RequiredMode.REQUIRED)
        String fileType,
        @Schema(description = "原始文件名", requiredMode = Schema.RequiredMode.REQUIRED)
        String fileName,
        @Schema(description = "文件媒体类型", requiredMode = Schema.RequiredMode.REQUIRED)
        String contentType,
        @Schema(description = "文件字节数", requiredMode = Schema.RequiredMode.REQUIRED)
        long fileSize,
        @Schema(
                description = "视频时长，单位秒；未探测时为空",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"integer", "null"})
        Integer durationSeconds,
        @Schema(
                description = "视频开始时间",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"},
                implementation = String.class,
                pattern = "^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$",
                example = "2026-09-29 12:00:00")
        OffsetDateTime startedAt,
        @Schema(
                description = "视频结束时间",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"},
                implementation = String.class,
                pattern = "^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$",
                example = "2026-09-29 12:00:00")
        OffsetDateTime endedAt,
        @Schema(
                description = "画面宽度，单位像素；未探测时为空",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"integer", "null"})
        Integer width,
        @Schema(
                description = "画面高度，单位像素；未探测时为空",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"integer", "null"})
        Integer height,
        @Schema(description = "文件处理状态", requiredMode = Schema.RequiredMode.REQUIRED)
        String status,
        @Schema(
                description = "视频处理状态；非视频或未建立处理记录时为空",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String videoStatus,
        @Schema(description = "错误码；无错误时为空", requiredMode = Schema.RequiredMode.REQUIRED, types = {"string", "null"})
        String errorCode,
        @Schema(
                description = "上传完成时间；未完成时为空",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"},
                implementation = String.class,
                pattern = "^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$",
                example = "2026-09-29 12:00:00")
        OffsetDateTime uploadedAt,
        @Schema(
                description = "文件创建时间",
                requiredMode = Schema.RequiredMode.REQUIRED,
                implementation = String.class,
                pattern = "^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$",
                example = "2026-09-29 12:00:00")
        OffsetDateTime createdAt,
        @Schema(
                description = "扩展元数据的原始 JSON 字符串；未设置时为空",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String metadata,
        @Schema(
                description = "正在处理的视频从开始时间累计的秒数；不适用时为空",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"integer", "null"})
        Integer elapsedSeconds) {
}
