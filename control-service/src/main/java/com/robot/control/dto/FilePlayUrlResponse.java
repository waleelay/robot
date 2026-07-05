package com.robot.control.dto;

import java.time.OffsetDateTime;

/**
 * 文件播放地址响应。
 *
 * @author leelay
 * @date 2026-07-05
 *
 * @param fileId 文件 ID
 * @param format 播放格式
 * @param contentType 内容类型
 * @param playUrl 播放地址
 * @param expiresAt 过期时间
 */
public record FilePlayUrlResponse(
        String fileId,
        String format,
        String contentType,
        String playUrl,
        OffsetDateTime expiresAt) {
}
