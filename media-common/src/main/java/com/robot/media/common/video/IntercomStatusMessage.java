package com.robot.media.common.video;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * 机器人客户端上报的对讲状态消息。
 *
 * @author leelay
 * @date 2026-07-05
 */
@Schema(name = "IntercomStatusMessage", description = "机器人客户端上报的对讲状态消息。")
public class IntercomStatusMessage {

    /**
     * 对讲依附的媒体业务会话 ID，用于定位服务端对讲状态。
     */
    @Schema(description = "视频会话 ID", types = {"string", "null"}) private String sessionId;
    /**
     * 设备侧对讲状态字符串，如 starting、active、interrupted、stopped 或 failed；与视频状态独立处理。
     */
    @Schema(description = "当前状态，具体状态转换见实时视频协议", types = {"string", "null"}) private String status;
    /**
     * 设备报告的机器人麦克风音轨 SID，在 active 状态写入会话。
     */
    @Schema(description = "机器人音频 Track SID", types = {"string", "null"}) private String robotAudioTrackSid;
    /**
     * 设备报告的机器人麦克风音轨名称；active 状态缺省时使用 audio.robot.mic。
     */
    @Schema(description = "机器人音频轨道名称", types = {"string", "null"}) private String robotAudioTrackName;
    /**
     * 设备报告的对讲失败原因编码；failed/error 未提供时服务端使用 INTERCOM_FAILED。
     */
    @Schema(description = "业务错误码", types = {"string", "null"}) private String errorCode;
    /**
     * 设备报告的对讲状态或失败说明，随相应事件传递。
     */
    @Schema(description = "状态或错误说明", types = {"string", "null"}) private String message;
    /**
     * 客户端生成此报告的时间；服务端状态转换使用接收时刻，不以此值排序。
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
     * 读取{@link #robotAudioTrackSid}。
     *
     * @return 当前值，含义与约束见{@link #robotAudioTrackSid}
     */
    public String getRobotAudioTrackSid() {
        return robotAudioTrackSid;
    }

    /**
     * 更新{@link #robotAudioTrackSid}。
     *
     * @param robotAudioTrackSid 新值，含义与约束见{@link #robotAudioTrackSid}
     */
    public void setRobotAudioTrackSid(String robotAudioTrackSid) {
        this.robotAudioTrackSid = robotAudioTrackSid;
    }

    /**
     * 读取{@link #robotAudioTrackName}。
     *
     * @return 当前值，含义与约束见{@link #robotAudioTrackName}
     */
    public String getRobotAudioTrackName() {
        return robotAudioTrackName;
    }

    /**
     * 更新{@link #robotAudioTrackName}。
     *
     * @param robotAudioTrackName 新值，含义与约束见{@link #robotAudioTrackName}
     */
    public void setRobotAudioTrackName(String robotAudioTrackName) {
        this.robotAudioTrackName = robotAudioTrackName;
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
