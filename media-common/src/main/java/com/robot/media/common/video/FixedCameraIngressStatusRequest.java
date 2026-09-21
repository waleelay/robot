package com.robot.media.common.video;

import java.util.List;

/** 固定摄像头 Ingress 批量状态查询。 */
public record FixedCameraIngressStatusRequest(List<String> cameraIds) {
}
