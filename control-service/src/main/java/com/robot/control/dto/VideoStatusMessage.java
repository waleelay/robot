package com.robot.control.dto;

import java.time.OffsetDateTime;

/**
 * 机器人客户端上报的视频状态消息。
 *
 * @author leelay
 * @date 2026-07-05
 */
public class VideoStatusMessage {

    private String sessionId;
    private String status;
    private String trackSid;
    private String trackName;
    private String errorCode;
    private String message;
    private OffsetDateTime timestamp;

    /**
     * 返回会话 ID。
     *
     * @return 会话 ID
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * 设置会话 ID。
     *
     * @param sessionId 会话 ID
     */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * 返回状态。
     *
     * @return 状态
     */
    public String getStatus() {
        return status;
    }

    /**
     * 设置状态。
     *
     * @param status 状态消息
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * 返回视频 Track SID。
     *
     * @return 视频 Track SID
     */
    public String getTrackSid() {
        return trackSid;
    }

    /**
     * 设置视频 Track SID。
     *
     * @param trackSid Track SID
     */
    public void setTrackSid(String trackSid) {
        this.trackSid = trackSid;
    }

    /**
     * 返回视频 Track 名称。
     *
     * @return 视频 Track 名称
     */
    public String getTrackName() {
        return trackName;
    }

    /**
     * 设置视频 Track 名称。
     *
     * @param trackName Track 名称
     */
    public void setTrackName(String trackName) {
        this.trackName = trackName;
    }

    /**
     * 返回错误码。
     *
     * @return 错误码
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * 设置错误码。
     *
     * @param errorCode 错误码
     */
    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    /**
     * 返回消息内容。
     *
     * @return 消息内容
     */
    public String getMessage() {
        return message;
    }

    /**
     * 设置消息内容。
     *
     * @param message 消息内容
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * 返回状态时间。
     *
     * @return 状态时间
     */
    public OffsetDateTime getTimestamp() {
        return timestamp;
    }

    /**
     * 设置状态时间。
     *
     * @param timestamp 状态时间
     */
    public void setTimestamp(OffsetDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
