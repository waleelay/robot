package com.robot.mediaserver.video.model;

/**
 * 固定摄像头上次核验的推流状态。
 */
public enum FixedCameraStreamStatus {
    /**
     * 最近观测到固定摄像头流在线。
     */
    ONLINE,
    /**
     * 最近观测到固定摄像头流离线。
     */
    OFFLINE,
    /**
     * 尚无足够新鲜的事实确认流状态。
     */
    UNKNOWN
}
