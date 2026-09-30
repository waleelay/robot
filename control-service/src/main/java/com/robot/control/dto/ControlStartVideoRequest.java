package com.robot.control.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import com.robot.media.common.video.VideoChannel;
import com.robot.media.common.video.VideoQuality;

/**
 * 前端从机器人摄像头入口启动视频的请求参数。
 *
 * @author leelay
 * @date 2026-07-05
 */
@Schema(name = "ControlStartVideoRequest", description = "前端从机器人摄像头入口启动视频的请求参数。")
public class ControlStartVideoRequest {

    /**
     * 视频通道。
     */
    @Schema(description = "视频通道", types = {"string", "null"}) private VideoChannel channel = VideoChannel.visible;
    /**
     * 视频清晰度。
     */
    @Schema(
            description = "视频清晰度；缺省取该入口的 DTO 默认值",
            types = {"string", "null"})
    private VideoQuality quality = VideoQuality.sub;
    /**
     * 是否复用会话。
     */
    @Schema(description = "是否请求复用现有会话；缺省取该入口的 DTO 默认值") private boolean reuse = true;
    /**
     * 客户端请求 ID。
     */
    @Schema(description = "客户端请求标识，用于关联会话创建请求", types = {"string", "null"}) private String clientRequestId;

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

    /**
     * 读取{@link #reuse}。
     *
     * @return 当前值，含义与约束见{@link #reuse}
     */
    public boolean isReuse() {
        return reuse;
    }

    /**
     * 更新{@link #reuse}。
     *
     * @param reuse 新值，含义与约束见{@link #reuse}
     */
    public void setReuse(boolean reuse) {
        this.reuse = reuse;
    }

    /**
     * 读取{@link #clientRequestId}。
     *
     * @return 当前值，含义与约束见{@link #clientRequestId}
     */
    public String getClientRequestId() {
        return clientRequestId;
    }

    /**
     * 更新{@link #clientRequestId}。
     *
     * @param clientRequestId 新值，含义与约束见{@link #clientRequestId}
     */
    public void setClientRequestId(String clientRequestId) {
        this.clientRequestId = clientRequestId;
    }
}
