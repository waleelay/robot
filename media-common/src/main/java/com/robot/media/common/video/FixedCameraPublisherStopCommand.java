package com.robot.media.common.video;

/** Control 需要精确下发给固定摄像头 Gateway 的停止命令。 */
public record FixedCameraPublisherStopCommand(
        String commandId,
        String sessionId,
        String cameraId,
        String roomName) {
}
