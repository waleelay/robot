package com.robot.mediaserver.video.messaging;

import java.time.OffsetDateTime;

/**
 * 云接入客户端 ACK 消息。
 *
 * @author leelay
 * @date 2026/05/20
 */
public class VideoAckMessage {

    private String commandId;
    private String sessionId;
    private boolean success = true;
    private String message;
    private OffsetDateTime timestamp;

    public String getCommandId() {
        return commandId;
    }

    public void setCommandId(String commandId) {
        this.commandId = commandId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
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
