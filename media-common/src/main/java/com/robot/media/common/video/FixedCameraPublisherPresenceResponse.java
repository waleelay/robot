package com.robot.media.common.video;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * 固定摄像头发布 Participant 与视频 Track 的实时存在性。
 *
 * @param cameraId 固定摄像头 ID
 * @param participantPresent 房间内是否存在预期参与者
 * @param trackPresent 是否存在预期视频轨道
 * @param observedAt 服务端观察时间
 */
@Schema(name = "FixedCameraPublisherPresenceResponse", description = "固定摄像头发布 Participant 与视频 Track 的实时存在性。")
public record FixedCameraPublisherPresenceResponse(
        @Schema(
                description = "固定摄像头 ID",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String cameraId,
        @Schema(description = "房间内是否存在预期参与者", requiredMode = Schema.RequiredMode.REQUIRED) boolean participantPresent,
        @Schema(description = "是否存在预期视频轨道", requiredMode = Schema.RequiredMode.REQUIRED) boolean trackPresent,
        @Schema(
                description = "服务端观察时间",
                requiredMode = Schema.RequiredMode.REQUIRED,
                implementation = String.class,
                pattern = "^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$",
                types = {"string", "null"})
        OffsetDateTime observedAt) {
}
