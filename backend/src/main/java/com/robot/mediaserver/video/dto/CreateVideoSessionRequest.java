package com.robot.mediaserver.video.dto;

import com.robot.mediaserver.video.model.VideoChannel;
import com.robot.mediaserver.video.model.VideoQuality;
import com.robot.mediaserver.video.model.VideoSourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 创建实时视频会话请求。
 *
 * @author leelay
 * @date 2026/05/19
 */
public class CreateVideoSessionRequest {

    @NotBlank
    private String robotId;

    @NotBlank
    private String deviceId;

    private VideoSourceType sourceType = VideoSourceType.ROBOT_CAMERA;
    private String sourceId;

    @NotNull
    private VideoChannel channel;

    @NotNull
    private VideoQuality quality = VideoQuality.sub;

    private boolean reuse = false;
    private String clientRequestId;

    public String getRobotId() {
        return robotId;
    }

    public void setRobotId(String robotId) {
        this.robotId = robotId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public VideoSourceType getSourceType() {
        return sourceType == null ? VideoSourceType.ROBOT_CAMERA : sourceType;
    }

    public void setSourceType(VideoSourceType sourceType) {
        this.sourceType = sourceType == null ? VideoSourceType.ROBOT_CAMERA : sourceType;
    }

    public String getSourceId() {
        return sourceId == null || sourceId.isBlank() ? robotId : sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

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
