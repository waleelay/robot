package com.robot.control.dto;

import java.util.List;

/**
 * 批量删除文件响应。
 *
 * @param total 请求总数
 * @param succeeded 成功数
 * @param failed 失败数
 * @param results 逐条删除结果
 */
public record FileBatchDeleteResponse(
        int total,
        int succeeded,
        int failed,
        List<FileDeleteResultResponse> results) {
}
