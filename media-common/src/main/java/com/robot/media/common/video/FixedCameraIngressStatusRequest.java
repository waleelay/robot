package com.robot.media.common.video;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 固定摄像头 Ingress 批量状态查询。
 *
 * @param cameraIds 固定摄像头 ID 列表
 */
@Schema(name = "FixedCameraIngressStatusRequest", description = "固定摄像头 Ingress 批量状态查询。")
public record FixedCameraIngressStatusRequest(@Schema(description = "固定摄像头 ID 列表", types = {"array", "null"}) List<String> cameraIds) {
}
