package com.robot.mediaserver.file.model;

/** 文件进入对象存储的上传方式，用于区分简单上传与分片会话。 */
public enum FileUploadMode {
    /**
     * 请求正文一次性上传文件。
     */
    SIMPLE,
    /**
     * 按平台分片会话直传暂存对象，校验后组合成源文件。
     */
    MULTIPART
}
