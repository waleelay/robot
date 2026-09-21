package com.robot.media.common.video;

import java.util.List;

/** 固定摄像头发布模式变更及待停止 Gateway 发布端。 */
public record FixedCameraPublisherModeResponse(
        String cameraId,
        VideoPublisherMode publisherMode,
        long publisherRevision,
        List<FixedCameraPublisherStopCommand> stopCommands) {
}
