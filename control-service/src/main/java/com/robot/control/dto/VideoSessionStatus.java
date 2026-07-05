package com.robot.control.dto;

/**
 * 视频会话状态枚举。
 *
 * @author leelay
 * @date 2026-07-05
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
