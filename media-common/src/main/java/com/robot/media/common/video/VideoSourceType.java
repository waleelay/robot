package com.robot.media.common.video;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 实时视频源类型。
 *
 * @author leelay
 * @date 2026-08-10
 */
@Schema(name = "VideoSourceType", description = "实时视频源类型。")
public enum VideoSourceType {
    /**
     * 机器人搭载的摄像头视频源。
     */
    ROBOT_CAMERA,
    /**
     * 管理端登记的固定摄像头视频源。
     */
    FIXED_CAMERA
}
