package com.robot.mediaserver.video.model;

/**
 * 实时视频会话状态。
 *
 * @author leelay
 * @date 2026/05/19
 */
public enum VideoSessionStatus {
    INIT,
    REQUESTING_CLIENT,
    ROOM_READY,
    STREAMING,
    INTERRUPTED,
    IDLE_WAIT,
    STOPPING,
    CLOSED,
    TIMEOUT,
    FAILED
}
