package com.robot.mediaserver.file.progress;

/**
 * 文件从上传到最终可用的对外统一阶段。
 *
 * <p>该阶段由文件状态和上传会话状态组合计算，供进度查询接口及前端展示使用，
 * 不直接持久化到数据库。</p>
 */
public enum FileProgressPhase {
    /** 正在上传文件内容或分片。 */
    UPLOADING,

    /** 分片已上传，服务端正在校验并合并源文件。 */
    FINALIZING,

    /** 源文件已完整保存，正在执行 HLS 转码等后处理。 */
    PROCESSING,

    /** 文件及其必要的衍生资源均已就绪，可以下载或播放。 */
    READY,

    /** 上传、合并或后处理失败。 */
    FAILED,

    /** 上传会话超过有效期且未完成。 */
    EXPIRED,

    /** 上传会话被主动终止。 */
    ABORTED,

    /** 文件已被逻辑删除。 */
    DELETED,

    /** 当前数据不足，无法判断文件所处阶段。 */
    UNKNOWN
}
