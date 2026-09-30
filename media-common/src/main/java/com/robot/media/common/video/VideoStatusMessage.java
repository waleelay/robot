package com.robot.media.common.video;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * 机器人客户端上报的视频状态消息。
 *
 * @author leelay
 * @date 2026-07-05
 */
@Schema(name = "VideoStatusMessage", description = "机器人客户端上报的视频状态消息。")
public class VideoStatusMessage {

    /**
     * 本次状态所属的媒体业务会话 ID，用于定位服务端视频状态机。
     */
    @Schema(description = "视频会话 ID", types = {"string", "null"}) private String sessionId;
    /**
     * 设备侧视频状态字符串；服务端归一化后处理，不能单独作为 LiveKit 轨道已可用的证明。
     */
    @Schema(description = "当前状态，具体状态转换见实时视频协议", types = {"string", "null"}) private String status;
    /**
     * 设备报告的视频轨道 SID；真实轨道状态仍由 LiveKit 事件和查询确认。
     */
    @Schema(description = "LiveKit 轨道标识", types = {"string", "null"}) private String trackSid;
    /**
     * 设备报告的视频轨道名称；缺省名称由服务端按通道和清晰度补齐。
     */
    @Schema(description = "媒体轨道名称", types = {"string", "null"}) private String trackName;
    /**
     * 设备侧发布失败或中断的原因编码；未上报时按服务端对应分支补默认值。
     */
    @Schema(description = "业务错误码", types = {"string", "null"}) private String errorCode;
    /**
     * 设备报告的状态或失败说明，不作为状态转换的判定键。
     */
    @Schema(description = "状态或错误说明", types = {"string", "null"}) private String message;
    /**
     * 客户端生成此状态报告的时间；服务端另用接收时间维护会话状态时间。
     */
    @Schema(
            description = "客户端状态报告时间",
            implementation = String.class,
            example = "2026-09-29T12:00:00+08:00",
            types = {"string", "null"})
    private OffsetDateTime timestamp;

    /**
     * 读取{@link #sessionId}。
     *
     * @return 当前值，含义与约束见{@link #sessionId}
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * 更新{@link #sessionId}。
     *
     * @param sessionId 新值，含义与约束见{@link #sessionId}
     */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * 读取{@link #status}。
     *
     * @return 当前值，含义与约束见{@link #status}
     */
    public String getStatus() {
        return status;
    }

    /**
     * 更新{@link #status}。
     *
     * @param status 新值，含义与约束见{@link #status}
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * 读取{@link #trackSid}。
     *
     * @return 当前值，含义与约束见{@link #trackSid}
     */
    public String getTrackSid() {
        return trackSid;
    }

    /**
     * 更新{@link #trackSid}。
     *
     * @param trackSid 新值，含义与约束见{@link #trackSid}
     */
    public void setTrackSid(String trackSid) {
        this.trackSid = trackSid;
    }

    /**
     * 读取{@link #trackName}。
     *
     * @return 当前值，含义与约束见{@link #trackName}
     */
    public String getTrackName() {
        return trackName;
    }

    /**
     * 更新{@link #trackName}。
     *
     * @param trackName 新值，含义与约束见{@link #trackName}
     */
    public void setTrackName(String trackName) {
        this.trackName = trackName;
    }

    /**
     * 读取{@link #errorCode}。
     *
     * @return 当前值，含义与约束见{@link #errorCode}
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * 更新{@link #errorCode}。
     *
     * @param errorCode 新值，含义与约束见{@link #errorCode}
     */
    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    /**
     * 读取{@link #message}。
     *
     * @return 当前值，含义与约束见{@link #message}
     */
    public String getMessage() {
        return message;
    }

    /**
     * 更新{@link #message}。
     *
     * @param message 新值，含义与约束见{@link #message}
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * 读取{@link #timestamp}。
     *
     * @return 当前值，含义与约束见{@link #timestamp}
     */
    public OffsetDateTime getTimestamp() {
        return timestamp;
    }

    /**
     * 更新{@link #timestamp}。
     *
     * @param timestamp 新值，含义与约束见{@link #timestamp}
     */
    public void setTimestamp(OffsetDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
