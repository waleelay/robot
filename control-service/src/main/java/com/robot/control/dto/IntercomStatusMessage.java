package com.robot.control.dto;

import java.time.OffsetDateTime;

public class IntercomStatusMessage {

    private String sessionId;
    private String status;
    private String robotAudioTrackSid;
    private String robotAudioTrackName;
    private String errorCode;
    private String message;
    private OffsetDateTime timestamp;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRobotAudioTrackSid() {
        return robotAudioTrackSid;
    }

    public void setRobotAudioTrackSid(String robotAudioTrackSid) {
        this.robotAudioTrackSid = robotAudioTrackSid;
    }

    public String getRobotAudioTrackName() {
        return robotAudioTrackName;
    }

    public void setRobotAudioTrackName(String robotAudioTrackName) {
        this.robotAudioTrackName = robotAudioTrackName;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public OffsetDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(OffsetDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
