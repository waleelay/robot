package com.robot.media.common.video;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Control 需要精确下发给固定摄像头 Gateway 的停止命令。
 *
 * @param commandId 指令 ID，用于关联客户端状态
 * @param sessionId 视频会话 ID
 * @param cameraId 固定摄像头 ID
 * @param roomName LiveKit 房间名
 */
@Schema(name = "FixedCameraPublisherStopCommand", description = "Control 需要精确下发给固定摄像头 Gateway 的停止命令。")
public record FixedCameraPublisherStopCommand(
        @Schema(
                description = "指令 ID，用于关联客户端状态",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String commandId,
        @Schema(
                description = "视频会话 ID",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String sessionId,
        @Schema(
                description = "固定摄像头 ID",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String cameraId,
        @Schema(
                description = "LiveKit 房间名",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String roomName) {
}
