package com.robot.media.common.video;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 对讲状态枚举。
 *
 * @author leelay
 * @date 2026-07-05
 */
@Schema(name = "IntercomStatus", description = "对讲状态枚举。")
public enum IntercomStatus {
    /**
     * 未占用对讲资源，可发起新的对讲。
     */
    IDLE,
    /**
     * 已申请对讲，等待机器人音频发布就绪。
     */
    STARTING,
    /**
     * 对讲处于活动状态，由操作端心跳维持。
     */
    ACTIVE,
    /**
     * 对讲媒体暂时中断，等待状态恢复或超时收口。
     */
    INTERRUPTED,
    /**
     * 正在停止对讲并释放占用。
     */
    STOPPING,
    /**
     * 对讲启动或运行失败，保留错误供调用方展示。
     */
    FAILED
}
