package com.robot.mediaserver.control.dto;

import com.robot.mediaserver.control.dto.VideoChannel;
import com.robot.mediaserver.control.dto.VideoQuality;

public class ControlStartVideoRequest {

    private VideoChannel channel = VideoChannel.visible;
    private VideoQuality quality = VideoQuality.sub;
    private boolean reuse = true;
    private String clientRequestId;

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

    public boolean isReuse() {
        return reuse;
    }

    public void setReuse(boolean reuse) {
        this.reuse = reuse;
    }

    public String getClientRequestId() {
        return clientRequestId;
    }

    public void setClientRequestId(String clientRequestId) {
        this.clientRequestId = clientRequestId;
    }
}
