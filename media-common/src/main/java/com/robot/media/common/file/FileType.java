package com.robot.media.common.file;

/**
 * 媒体文件类型枚举。
 *
 * @author leelay
 * @date 2026-07-05
 */
public enum FileType {
    /**
     * 视频文件，可能需要探测或生成 HLS。
     */
    VIDEO,
    /**
     * 音频文件。
     */
    AUDIO,
    /**
     * 图片文件，包括前端视频抓拍。
     */
    IMAGE,
    /**
     * 日志类文件。
     */
    LOG,
    /**
     * 配置类文件。
     */
    CONFIG,
    /**
     * 地图及相关资源文件。
     */
    MAP,
    /**
     * 文档类文件。
     */
    DOCUMENT,
    /**
     * 未归入其他明确类型的文件。
     */
    OTHER
}
