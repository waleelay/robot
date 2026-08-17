package com.robot.media.common.file;

/**
 * 单个文件的删除结果。
 *
 * @param fileId 文件 ID
 * @param success 是否删除成功
 * @param code 结果编码
 * @param message 结果说明
 */
public record FileDeleteResultResponse(
        String fileId,
        boolean success,
        String code,
        String message) {
}
