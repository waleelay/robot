package com.robot.control.dto;

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
