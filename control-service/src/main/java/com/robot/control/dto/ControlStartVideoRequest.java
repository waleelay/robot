package com.robot.control.dto;

import com.robot.control.dto.VideoChannel;
import com.robot.control.dto.VideoQuality;

/**
 * 前端从机器人摄像头入口启动视频的请求参数。
 *
 * @author leelay
 * @date 2026-07-05
 */
public class ControlStartVideoRequest {

    private VideoChannel channel = VideoChannel.visible;
    private VideoQuality quality = VideoQuality.sub;
    private boolean reuse = true;
    private String clientRequestId;

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

    /**
     * 返回是否复用会话。
     *
     * @return 是否复用会话
     */
    public boolean isReuse() {
        return reuse;
    }

    /**
     * 设置是否复用会话。
     *
     * @param reuse 是否复用会话
     */
    public void setReuse(boolean reuse) {
        this.reuse = reuse;
    }

    /**
     * 返回客户端请求 ID。
     *
     * @return 客户端请求 ID
     */
    public String getClientRequestId() {
        return clientRequestId;
    }

    /**
     * 设置客户端请求 ID。
     *
     * @param clientRequestId 客户端请求 ID
     */
    public void setClientRequestId(String clientRequestId) {
        this.clientRequestId = clientRequestId;
    }
}
