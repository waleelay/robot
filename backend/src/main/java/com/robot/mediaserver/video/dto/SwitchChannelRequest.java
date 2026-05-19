package com.robot.mediaserver.video.dto;

import com.robot.mediaserver.video.model.VideoChannel;
import com.robot.mediaserver.video.model.VideoQuality;
import jakarta.validation.constraints.NotNull;

/**
 * 切换媒体通道请求。
 *
 * @author leelay
 * @date 2026/05/19
 */
public class SwitchChannelRequest {

    @NotNull
    private VideoChannel channel;

    private VideoQuality quality;

    public VideoChannel getChannel() {
        return channel;
    }

    public void setChannel(VideoChannel channel) {
        this.channel = channel;
    }

    public VideoQuality getQuality() {
        return quality;
    }

    public void setQuality(VideoQuality quality) {
        this.quality = quality;
    }
}
