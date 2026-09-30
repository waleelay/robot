package com.robot.media.common.video;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 视频会话状态枚举。
 *
 * @author leelay
 * @date 2026-07-05
 */
@Schema(name = "VideoSessionStatus", description = "视频会话状态枚举。")
public enum VideoSessionStatus {
    /**
     * 会话已建立，尚未完成媒体发布准备。
     */
    INIT,
    /**
     * 已请求设备发布，等待发布端响应及轨道出现。
     */
    REQUESTING_CLIENT,
    /**
     * 房间准备完成，尚未确认有效视频轨道。
     */
    ROOM_READY,
    /**
     * 已由 LiveKit 事实确认有效视频轨道。
     */
    STREAMING,
    /**
     * 原媒体发布已中断，仍可能按恢复策略重启。
     */
    INTERRUPTED,
    /**
     * 没有有效观看者，处于延迟释放窗口。
     */
    IDLE_WAIT,
    /**
     * 正在停止媒体发布并收口会话。
     */
    STOPPING,
    /**
     * 会话已关闭，不再作为活动观看会话使用。
     */
    CLOSED,
    /**
     * 发布或等待过程超过允许时限。
     */
    TIMEOUT,
    /**
     * 会话发生不可继续的业务错误。
     */
    FAILED
}
