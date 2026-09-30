package com.robot.media.common.video;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 视频源发布端模式。
 */
@Schema(name = "VideoPublisherMode", description = "视频源发布端模式。")
public enum VideoPublisherMode {
    /**
     * 由机器人侧客户端发布媒体。
     */
    DEVICE_CLIENT,
    /**
     * 由固定摄像头 Gateway 拉取 RTSP 并发布媒体。
     */
    FIXED_CAMERA_GATEWAY,
    /**
     * 由 LiveKit Ingress 接收固定摄像头 RTMP 推流。
     */
    LIVEKIT_INGRESS
}
