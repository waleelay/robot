package com.robot.control.dto;

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
 */
public record FileListItemResponse(
        String fileId,
        String robotId,
        String deviceId,
        String extensionId,
        String fileType,
        String fileName,
        String contentType,
        long fileSize,
        Integer durationSeconds,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt,
        Integer width,
        Integer height,
        String status,
        String videoStatus,
        String errorCode,
        OffsetDateTime uploadedAt,
        OffsetDateTime createdAt,
        String metadata) {
}
