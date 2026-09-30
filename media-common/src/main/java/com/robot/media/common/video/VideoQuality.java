package com.robot.media.common.video;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 视频清晰度枚举。
 *
 * @author leelay
 * @date 2026-07-05
 */
@Schema(name = "VideoQuality", description = "视频清晰度枚举。")
public enum VideoQuality {
    /**
     * 子码流，视频墙默认优先使用。
     */
    sub,
    /**
     * 主码流，适用于单路高质量观看。
     */
    main,
    /**
     * 由设备或 Gateway 按能力选择码流；固定摄像头优先子码流。
     */
    auto
}
