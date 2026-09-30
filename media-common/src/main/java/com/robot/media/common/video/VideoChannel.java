package com.robot.media.common.video;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 视频通道枚举。
 *
 * @author leelay
 * @date 2026-07-05
 */
@Schema(name = "VideoChannel", description = "视频通道枚举。")
public enum VideoChannel {
    /**
     * 可见光视频通道。
     */
    visible,
    /**
     * 热成像视频通道。
     */
    thermal,
    /**
     * 融合视频通道，需要设备提供对应视频源。
     */
    fusion
}
