package com.robot.mediaserver.file.model;

/**
 * 分片上传会话的内部状态。
 *
 * <p>该状态持久化在 {@code media_file_upload.status}，用于控制分片上传、源文件合并、
 * 会话过期和清理流程，不直接作为前端展示的文件进度阶段。</p>
 */
public enum FileUploadStatus {
    /** 上传会话有效，客户端可以继续上传分片。 */
    ACTIVE,

    /** 所有分片已提交，服务端正在校验并合并源文件。 */
    COMPLETING,

    /** 源文件已合并完成；视频仍可能处于 HLS 转码等后处理阶段。 */
    COMPLETED,

    /** 上传会话已超过有效期。 */
    EXPIRED,

    /** 上传会话被主动终止，例如文件被删除。 */
    ABORTED,

    /** 上传会话因不可恢复的错误而失败。 */
    FAILED
}
