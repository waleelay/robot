package com.robot.mediaserver.video.model;

/**
 * 视频会话内嵌的双向语音对讲状态。
 */
public enum IntercomStatus {
    IDLE,
    STARTING,
    ACTIVE,
    INTERRUPTED,
    STOPPING,
    FAILED
}
