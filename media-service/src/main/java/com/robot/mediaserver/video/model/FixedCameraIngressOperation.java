package com.robot.mediaserver.video.model;

/**
 * 固定摄像头 Ingress 已接纳操作类型。
 */
public enum FixedCameraIngressOperation {
    /**
     * 创建摄像头对应的 Ingress 接入资源。
     */
    CREATE,
    /**
     * 轮换推流凭据，旧凭据不可继续作为当前凭据使用。
     */
    ROTATE,
    /**
     * 撤销摄像头的 Ingress 接入资源。
     */
    REVOKE
}
