package com.robot.media.common.file;

/**
 * 媒体文件处理状态枚举。
 *
 * @author leelay
 * @date 2026-07-05
 */
public enum FileStatus {
    /**
     * 文件内容尚在上传，不能作为就绪文件消费。
     */
    UPLOADING,
    /**
     * 内容已接收，媒体探测或后处理尚未完成。
     */
    PROCESSING,
    /**
     * 文件已就绪，可按对应类型下载或播放。
     */
    READY,
    /**
     * 上传或后处理失败，可查询关联错误。
     */
    FAILED,
    /**
     * 文件已逻辑删除，后续资产清理由服务处理。
     */
    DELETED
}
