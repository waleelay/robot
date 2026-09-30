package com.robot.mediaserver.file.model;

/** 视频处理状态，与文件主记录共同区分上传、转码、可播放和失败阶段。 */
public enum VideoFileStatus {
    /**
     * 视频资源正在探测、转码或生成播放产物。
     */
    PROCESSING,
    /**
     * 视频播放产物已就绪。
     */
    READY,
    /**
     * 视频后处理失败，需要检查错误原因。
     */
    FAILED
}
