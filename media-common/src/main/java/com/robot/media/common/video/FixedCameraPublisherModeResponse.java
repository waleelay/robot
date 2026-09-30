package com.robot.media.common.video;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 固定摄像头发布模式变更及待停止 Gateway 发布端。
 *
 * @param cameraId 固定摄像头 ID
 * @param publisherMode 当前发布模式
 * @param publisherRevision 发布模式版本，用于拒绝陈旧发布者操作
 * @param stopCommands 发布模式切换时需要下发给旧发布者的停止命令
 */
@Schema(name = "FixedCameraPublisherModeResponse", description = "固定摄像头发布模式变更及待停止 Gateway 发布端。")
public record FixedCameraPublisherModeResponse(
        @Schema(
                description = "固定摄像头 ID",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String cameraId,
        @Schema(
                description = "当前发布模式",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        VideoPublisherMode publisherMode,
        @Schema(description = "发布模式版本，用于拒绝陈旧发布者操作", requiredMode = Schema.RequiredMode.REQUIRED) long publisherRevision,
        @Schema(
                description = "发布模式切换时需要下发给旧发布者的停止命令",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"array", "null"})
        List<FixedCameraPublisherStopCommand> stopCommands) {
}
