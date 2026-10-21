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
     * 由统一边缘媒体 Gateway 接入视频源并发布媒体，与业务设备类型解耦。
     */
    EDGE_MEDIA_GATEWAY,
    /**
     * 由 LiveKit Ingress 接收固定摄像头 RTMP 推流。
     */
    LIVEKIT_INGRESS
}
