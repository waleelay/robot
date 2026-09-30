package com.robot.media.common.video;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 控制服务 调用 媒体服务 创建视频会话的请求参数。
 *
 * @author leelay
 * @date 2026-07-05
 */
@Schema(name = "CreateVideoSessionRequest", description = "Control Service 调用 Media Service 创建视频会话的请求参数。")
public class CreateVideoSessionRequest {

    /**
     * 机器人 ID。
     */
    @Schema(description = "机器人 ID；固定摄像头场景按现有来源映射填写", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
    private String robotId;

    /**
     * 设备 ID。
     */
    @Schema(description = "相机或设备组件 ID", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
    private String deviceId;

    /**
     * 请求的视频源类型；初始值为 ROBOT_CAMERA，访问器也对 null 回退到该值。
     */
    @Schema(
            description = "视频来源类型",
            types = {"string", "null"})
    private VideoSourceType sourceType = VideoSourceType.ROBOT_CAMERA;
    /**
     * 请求显式传入的视频源 ID；为空或空白时 getSourceId 返回 robotId。
     */
    @Schema(description = "视频源 ID；创建请求缺省时使用 robotId", types = {"string", "null"}) private String sourceId;
    /**
     * 调用方预期的发布模式。
     */
    @Schema(description = "调用方预期的发布模式", types = {"string", "null"}) private VideoPublisherMode expectedPublisherMode;
    /**
     * 调用方预期的发布模式版本。
     */
    @Schema(description = "调用方预期的发布模式版本", types = {"integer", "null"}) private Long expectedPublisherRevision;

    /**
     * 视频通道。
     */
    @Schema(description = "视频通道", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
    private VideoChannel channel;

    /**
     * 请求的视频清晰度；缺省 sub，显式传入 null 会被请求校验拒绝。
     */
    @Schema(
            description = "视频清晰度；缺省 sub，显式 null 校验失败",
            defaultValue = "sub",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @NotNull
    private VideoQuality quality = VideoQuality.sub;

    /**
     * 是否请求复用已有会话；此请求模型初始值为 false，具体复用条件由服务端校验。
     */
    @Schema(description = "是否请求复用现有会话；缺省取该入口的 DTO 默认值") private boolean reuse;
    /**
     * 客户端请求 ID。
     */
    @Schema(description = "客户端请求标识，用于关联会话创建请求", types = {"string", "null"}) private String clientRequestId;

    /**
     * 读取{@link #robotId}。
     *
     * @return 当前值，含义与约束见{@link #robotId}
     */
    public String getRobotId() {
        return robotId;
    }

    /**
     * 更新{@link #robotId}。
     *
     * @param robotId 新值，含义与约束见{@link #robotId}
     */
    public void setRobotId(String robotId) {
        this.robotId = robotId;
    }

    /**
     * 读取{@link #deviceId}。
     *
     * @return 当前值，含义与约束见{@link #deviceId}
     */
    public String getDeviceId() {
        return deviceId;
    }

    /**
     * 更新{@link #deviceId}。
     *
     * @param deviceId 新值，含义与约束见{@link #deviceId}
     */
    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    /**
     * 读取请求的视频源类型；缺省沿用机器人摄像头。
     *
     * @return 有效视频源类型
     */
    public VideoSourceType getSourceType() {
        return sourceType == null ? VideoSourceType.ROBOT_CAMERA : sourceType;
    }

    /**
     * 设置视频源类型，null 按当前请求默认值处理。
     *
     * @param sourceType 视频来源类型
     */
    public void setSourceType(VideoSourceType sourceType) {
        this.sourceType = sourceType == null ? VideoSourceType.ROBOT_CAMERA : sourceType;
    }

    /**
     * 读取视频源标识；缺省时使用请求中的 robotId。
     *
     * @return 有效视频源标识
     */
    public String getSourceId() {
        return sourceId == null || sourceId.isBlank() ? robotId : sourceId;
    }

    /**
     * 更新{@link #sourceId}。
     *
     * @param sourceId 新值，含义与约束见{@link #sourceId}
     */
    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    /**
     * 读取{@link #expectedPublisherMode}。
     *
     * @return 当前值，含义与约束见{@link #expectedPublisherMode}
     */
    public VideoPublisherMode getExpectedPublisherMode() {
        return expectedPublisherMode;
    }

    /**
     * 更新{@link #expectedPublisherMode}。
     *
     * @param expectedPublisherMode 新值，含义与约束见{@link #expectedPublisherMode}
     */
    public void setExpectedPublisherMode(VideoPublisherMode expectedPublisherMode) {
        this.expectedPublisherMode = expectedPublisherMode;
    }

    /**
     * 读取{@link #expectedPublisherRevision}。
     *
     * @return 当前值，含义与约束见{@link #expectedPublisherRevision}
     */
    public Long getExpectedPublisherRevision() {
        return expectedPublisherRevision;
    }

    /**
     * 更新{@link #expectedPublisherRevision}。
     *
     * @param expectedPublisherRevision 新值，含义与约束见{@link #expectedPublisherRevision}
     */
    public void setExpectedPublisherRevision(Long expectedPublisherRevision) {
        this.expectedPublisherRevision = expectedPublisherRevision;
    }

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
