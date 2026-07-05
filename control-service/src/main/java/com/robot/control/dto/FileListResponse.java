package com.robot.control.dto;

import java.util.List;

/**
 * 分页文件列表响应。
 *
 * @author leelay
 * @date 2026-07-05
 *
 * @param items 列表项
 * @param page 页码
 * @param size 分页大小
 * @param total 总数
 */
public record FileListResponse(List<FileListItemResponse> items, int page, int size, long total) {
}
