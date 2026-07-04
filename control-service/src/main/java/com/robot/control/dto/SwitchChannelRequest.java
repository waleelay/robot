package com.robot.control.dto;

import jakarta.validation.constraints.NotNull;

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
