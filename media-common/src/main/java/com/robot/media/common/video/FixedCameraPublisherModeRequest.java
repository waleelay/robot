package com.robot.media.common.video;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;

/**
 * 固定摄像头发布模式切换请求。
 *
 * @param targetMode 目标发布模式
 */
@Schema(name = "FixedCameraPublisherModeRequest", description = "固定摄像头发布模式切换请求。")
public record FixedCameraPublisherModeRequest(@Schema(description = "目标发布模式", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull VideoPublisherMode targetMode) {
}
