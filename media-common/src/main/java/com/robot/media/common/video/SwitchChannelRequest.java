package com.robot.media.common.video;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;

/**
 * 切换视频通道请求。
 *
 * @author leelay
 * @date 2026-07-05
 */
@Schema(name = "SwitchChannelRequest", description = "切换视频通道请求。")
public class SwitchChannelRequest {

    /**
     * 视频通道。
     */
    @Schema(description = "视频通道", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
    private VideoChannel channel;

    /**
     * 视频清晰度。
     */
    @Schema(description = "视频清晰度；缺省取该入口的 DTO 默认值", types = {"string", "null"}) private VideoQuality quality;

    /**
     * 读取{@link #channel}。
     *
     * @return 当前值，含义与约束见{@link #channel}
     */
    public VideoChannel getChannel() {
        return channel;
    }

    /**
     * 更新{@link #channel}。
     *
     * @param channel 新值，含义与约束见{@link #channel}
     */
    public void setChannel(VideoChannel channel) {
        this.channel = channel;
    }

    /**
     * 读取{@link #quality}。
     *
     * @return 当前值，含义与约束见{@link #quality}
     */
    public VideoQuality getQuality() {
        return quality;
    }

    /**
     * 更新{@link #quality}。
     *
     * @param quality 新值，含义与约束见{@link #quality}
     */
    public void setQuality(VideoQuality quality) {
        this.quality = quality;
    }
}
