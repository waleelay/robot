package com.robot.mediaserver.file.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** 视频文件的 HLS 处理记录，保存媒体探测结果、播放位置和处理阶段时间。 */
@Entity
@Table(name = "media_video_file")
public class MediaVideoFile {

    /**
     * 文件 ID。
     */
    @Id
    @Column(length = 64)
    private String fileId;

    /**
     * 视频编码名称。
     */
    @Column(length = 32)
    private String videoCodec;

    /**
     * 音频编码名称，未探测到音轨时可为空。
     */
    @Column(length = 32)
    private String audioCodec;

    /**
     * 时长秒数。
     */
    private Integer durationSeconds;
    /**
     * 开始时间。
     */
    private OffsetDateTime startedAt;
    /**
     * 结束时间。
     */
    private OffsetDateTime endedAt;
    /**
     * 视频画面宽度，单位像素；尚未探测到时为空。
     */
    private Integer width;
    /**
     * 视频画面高度，单位像素；尚未探测到时为空。
     */
    private Integer height;

    /**
     * HLS 主播放列表的对象存储键。
     */
    @Column(length = 512)
    private String hlsPlaylistObjectKey;

    /**
     * 已生成的 HLS 媒体分片数。
     */
    private Integer hlsSegmentCount;
    /**
     * HLS 播放列表和分片的总字节数。
     */
    private Long hlsTotalSize;

    /**
     * 视频后处理状态，区分处理中、播放产物就绪和处理失败，独立于通用文件状态。
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private VideoFileStatus status;

    /**
     * 错误码。
     */
    @Column(length = 64)
    private String errorCode;

    /**
     * 失败原因；无错误时为空。
     */
    @Column(length = 512)
    private String errorMessage;

    /**
     * 媒体后处理开始时间。
     */
    private OffsetDateTime processingStartedAt;
    /**
     * 媒体后处理结束时间。
     */
    private OffsetDateTime processingCompletedAt;

    /**
     * 读取{@link #fileId}。
     *
     * @return 当前值，含义与约束见{@link #fileId}
     */
    public String getFileId() { return fileId; }
    /**
     * 更新{@link #fileId}。
     *
     * @param fileId 新值，含义与约束见{@link #fileId}
     */
    public void setFileId(String fileId) { this.fileId = fileId; }
    /**
     * 读取{@link #videoCodec}。
     *
     * @return 当前值，含义与约束见{@link #videoCodec}
     */
    public String getVideoCodec() { return videoCodec; }
    /**
     * 更新{@link #videoCodec}。
     *
     * @param videoCodec 新值，含义与约束见{@link #videoCodec}
     */
    public void setVideoCodec(String videoCodec) { this.videoCodec = videoCodec; }
    /**
     * 读取{@link #audioCodec}。
     *
     * @return 当前值，含义与约束见{@link #audioCodec}
     */
    public String getAudioCodec() { return audioCodec; }
    /**
     * 更新{@link #audioCodec}。
     *
     * @param audioCodec 新值，含义与约束见{@link #audioCodec}
     */
    public void setAudioCodec(String audioCodec) { this.audioCodec = audioCodec; }
    /**
     * 读取{@link #durationSeconds}。
     *
     * @return 当前值，含义与约束见{@link #durationSeconds}
     */
    public Integer getDurationSeconds() { return durationSeconds; }
    /**
     * 更新{@link #durationSeconds}。
     *
     * @param durationSeconds 新值，含义与约束见{@link #durationSeconds}
     */
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }
    /**
     * 读取{@link #startedAt}。
     *
     * @return 当前值，含义与约束见{@link #startedAt}
     */
    public OffsetDateTime getStartedAt() { return startedAt; }
    /**
     * 更新{@link #startedAt}。
     *
     * @param startedAt 新值，含义与约束见{@link #startedAt}
     */
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }
    /**
     * 读取{@link #endedAt}。
     *
     * @return 当前值，含义与约束见{@link #endedAt}
     */
    public OffsetDateTime getEndedAt() { return endedAt; }
    /**
     * 更新{@link #endedAt}。
     *
     * @param endedAt 新值，含义与约束见{@link #endedAt}
     */
    public void setEndedAt(OffsetDateTime endedAt) { this.endedAt = endedAt; }
    /**
     * 读取{@link #width}。
     *
     * @return 当前值，含义与约束见{@link #width}
     */
    public Integer getWidth() { return width; }
    /**
     * 更新{@link #width}。
     *
     * @param width 新值，含义与约束见{@link #width}
     */
    public void setWidth(Integer width) { this.width = width; }
    /**
     * 读取{@link #height}。
     *
     * @return 当前值，含义与约束见{@link #height}
     */
    public Integer getHeight() { return height; }
    /**
     * 更新{@link #height}。
     *
     * @param height 新值，含义与约束见{@link #height}
     */
    public void setHeight(Integer height) { this.height = height; }
    /**
     * 读取{@link #hlsPlaylistObjectKey}。
     *
     * @return 当前值，含义与约束见{@link #hlsPlaylistObjectKey}
     */
    public String getHlsPlaylistObjectKey() { return hlsPlaylistObjectKey; }
    /**
     * 更新{@link #hlsPlaylistObjectKey}。
     *
     * @param hlsPlaylistObjectKey 新值，含义与约束见{@link #hlsPlaylistObjectKey}
     */
    public void setHlsPlaylistObjectKey(String hlsPlaylistObjectKey) { this.hlsPlaylistObjectKey = hlsPlaylistObjectKey; }
    /**
     * 读取{@link #hlsSegmentCount}。
     *
     * @return 当前值，含义与约束见{@link #hlsSegmentCount}
     */
    public Integer getHlsSegmentCount() { return hlsSegmentCount; }
    /**
     * 更新{@link #hlsSegmentCount}。
     *
     * @param hlsSegmentCount 新值，含义与约束见{@link #hlsSegmentCount}
     */
    public void setHlsSegmentCount(Integer hlsSegmentCount) { this.hlsSegmentCount = hlsSegmentCount; }
    /**
     * 读取{@link #hlsTotalSize}。
     *
     * @return 当前值，含义与约束见{@link #hlsTotalSize}
     */
    public Long getHlsTotalSize() { return hlsTotalSize; }
    /**
     * 更新{@link #hlsTotalSize}。
     *
     * @param hlsTotalSize 新值，含义与约束见{@link #hlsTotalSize}
     */
    public void setHlsTotalSize(Long hlsTotalSize) { this.hlsTotalSize = hlsTotalSize; }
    /**
     * 读取{@link #status}。
     *
     * @return 当前值，含义与约束见{@link #status}
     */
    public VideoFileStatus getStatus() { return status; }
    /**
     * 更新{@link #status}。
     *
     * @param status 新值，含义与约束见{@link #status}
     */
    public void setStatus(VideoFileStatus status) { this.status = status; }
    /**
     * 读取{@link #errorCode}。
     *
     * @return 当前值，含义与约束见{@link #errorCode}
     */
    public String getErrorCode() { return errorCode; }
    /**
     * 更新{@link #errorCode}。
     *
     * @param errorCode 新值，含义与约束见{@link #errorCode}
     */
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    /**
     * 读取{@link #errorMessage}。
     *
     * @return 当前值，含义与约束见{@link #errorMessage}
     */
    public String getErrorMessage() { return errorMessage; }
    /**
     * 更新{@link #errorMessage}。
     *
     * @param errorMessage 新值，含义与约束见{@link #errorMessage}
     */
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    /**
     * 读取{@link #processingStartedAt}。
     *
     * @return 当前值，含义与约束见{@link #processingStartedAt}
     */
    public OffsetDateTime getProcessingStartedAt() { return processingStartedAt; }
    /**
     * 更新{@link #processingStartedAt}。
     *
     * @param processingStartedAt 新值，含义与约束见{@link #processingStartedAt}
     */
    public void setProcessingStartedAt(OffsetDateTime processingStartedAt) { this.processingStartedAt = processingStartedAt; }
    /**
     * 读取{@link #processingCompletedAt}。
     *
     * @return 当前值，含义与约束见{@link #processingCompletedAt}
     */
    public OffsetDateTime getProcessingCompletedAt() { return processingCompletedAt; }
    /**
     * 更新{@link #processingCompletedAt}。
     *
     * @param processingCompletedAt 新值，含义与约束见{@link #processingCompletedAt}
     */
    public void setProcessingCompletedAt(OffsetDateTime processingCompletedAt) { this.processingCompletedAt = processingCompletedAt; }
}
