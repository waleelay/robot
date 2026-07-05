package com.robot.control.dto;

import java.time.OffsetDateTime;

/**
 * 文件下载地址响应。
 *
 * @author leelay
 * @date 2026-07-05
 *
 * @param fileId 文件 ID
 * @param downloadUrl 下载地址
 * @param expiresAt 过期时间
 */
public record FileDownloadUrlResponse(String fileId, String downloadUrl, OffsetDateTime expiresAt) {
}
