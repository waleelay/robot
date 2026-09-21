package com.robot.media.common.video;

import jakarta.validation.constraints.NotNull;

/** 固定摄像头发布模式切换请求。 */
public record FixedCameraPublisherModeRequest(@NotNull VideoPublisherMode targetMode) {
}
