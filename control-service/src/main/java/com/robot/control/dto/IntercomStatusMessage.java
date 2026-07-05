package com.robot.control.dto;

import java.time.OffsetDateTime;

/**
 * 机器人客户端上报的对讲状态消息。
 *
 * @author leelay
 * @date 2026-07-05
 */
public class IntercomStatusMessage {

    private String sessionId;
    private String status;
    private String robotAudioTrackSid;
    private String robotAudioTrackName;
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
     * 返回机器人音频 Track SID。
     *
     * @return 机器人音频 Track SID
     */
    public String getRobotAudioTrackSid() {
        return robotAudioTrackSid;
    }

    /**
     * 设置机器人音频 Track SID。
     *
     * @param robotAudioTrackSid 机器人音频 Track SID
     */
    public void setRobotAudioTrackSid(String robotAudioTrackSid) {
        this.robotAudioTrackSid = robotAudioTrackSid;
    }

    /**
     * 返回机器人音频 Track 名称。
     *
     * @return 机器人音频 Track 名称
     */
    public String getRobotAudioTrackName() {
        return robotAudioTrackName;
    }

    /**
     * 设置机器人音频 Track 名称。
     *
     * @param robotAudioTrackName 机器人音频 Track 名称
     */
    public void setRobotAudioTrackName(String robotAudioTrackName) {
        this.robotAudioTrackName = robotAudioTrackName;
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
