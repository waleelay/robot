package com.robot.control.dto;

import java.util.List;

/**
 * 固定摄像头批量启动视频请求。
 *
 * @author leelay
 * @date 2026-08-10
 */
public class FixedCameraBatchStartRequest extends ControlStartVideoRequest {

    private List<String> cameraIds = List.of();

    public List<String> getCameraIds() {
        return cameraIds;
    }

    public void setCameraIds(List<String> cameraIds) {
        this.cameraIds = cameraIds == null ? List.of() : cameraIds;
    }
}
