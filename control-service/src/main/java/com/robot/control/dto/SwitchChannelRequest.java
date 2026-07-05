package com.robot.control.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 切换视频通道请求。
 *
 * @author leelay
 * @date 2026-07-05
 */
public class SwitchChannelRequest {

    @NotNull
    private VideoChannel channel;

    private VideoQuality quality;

    /**
     * 返回视频通道。
     *
     * @return 视频通道
     */
    public VideoChannel getChannel() {
        return channel;
    }

    /**
     * 设置视频通道。
     *
     * @param channel 视频通道
     */
    public void setChannel(VideoChannel channel) {
        this.channel = channel;
    }

    /**
     * 返回视频清晰度。
     *
     * @return 视频清晰度
     */
    public VideoQuality getQuality() {
        return quality;
    }

    /**
     * 设置视频清晰度。
     *
     * @param quality 视频清晰度
     */
    public void setQuality(VideoQuality quality) {
        this.quality = quality;
    }
}
