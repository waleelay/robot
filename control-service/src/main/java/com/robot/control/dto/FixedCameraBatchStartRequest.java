package com.robot.control.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 固定摄像头批量启动视频请求。
 *
 * @author leelay
 * @date 2026-08-10
 */
@Schema(name = "FixedCameraBatchStartRequest", description = "固定摄像头批量启动视频请求。")
public class FixedCameraBatchStartRequest extends ControlStartVideoRequest {

    /**
     * 固定摄像头 ID 列表。
     */
    @Schema(description = "固定摄像头 ID 列表", types = {"array", "null"}) private List<String> cameraIds = List.of();

    /**
     * 读取{@link #cameraIds}。
     *
     * @return 当前值，含义与约束见{@link #cameraIds}
     */
    public List<String> getCameraIds() {
        return cameraIds;
    }

    /**
     * 接收批量摄像头标识并按当前请求约定归一化集合。
     *
     * @param cameraIds 固定摄像头 ID 列表
     */
    public void setCameraIds(List<String> cameraIds) {
        this.cameraIds = cameraIds == null ? List.of() : cameraIds;
    }
}
