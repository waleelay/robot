package com.robot.mediaserver.video.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * 实时视频会话实体。
 *
 * <p>该实体保存平台侧业务会话状态，不保存媒体流本身。媒体流由云接入客户端
 * 发布到 LiveKit，并由前端直接订阅。</p>
 *
 * @author leelay
 * @date 2026/05/19
 */
@Entity
@Table(
        name = "media_video_session",
        indexes = {
                @Index(name = "idx_video_session_device_status", columnList = "robotId,deviceId,channel,status"),
                @Index(name = "idx_video_session_created_by", columnList = "createdBy,createdAt")
        })
public class VideoSession {

    @Id
    @Column(length = 64)
    private String sessionId;

    @Column(nullable = false, length = 64)
    private String robotId;

    @Column(nullable = false, length = 64)
    private String deviceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private VideoChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private VideoQuality quality;

    @Column(nullable = false, length = 160)
    private String roomName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private VideoSessionStatus status;

    @Column(nullable = false)
    private int viewerCount;

    @Enumerated(EnumType.STRING)
    @Column(length = 24)
    private IntercomStatus intercomStatus = IntercomStatus.IDLE;

    @Column(nullable = false)
    private boolean intercomAudioOnly;

    @Column(length = 64)
    private String intercomOperatorId;

    @Column(length = 128)
    private String intercomClientId;

    @Column(length = 128)
    private String robotAudioTrackSid;

    @Column(length = 128)
    private String robotAudioTrackName;

    private OffsetDateTime intercomStartedAt;
    private OffsetDateTime intercomHeartbeatAt;

    @Column(length = 128)
    private String trackSid;

    @Column(length = 128)
    private String trackName;

    @Column(length = 64)
    private String commandId;

    @Column(length = 64)
    private String orgId;

    @Column(length = 64)
    private String createdBy;

    private OffsetDateTime startedAt;
    private OffsetDateTime endedAt;
    private OffsetDateTime commandRequestedAt;
    private OffsetDateTime lastStatusAt;
    private OffsetDateTime idleSince;

    @Column(length = 64)
    private String lastErrorCode;

    @Column(length = 512)
    private String lastErrorMessage;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getRobotId() {
        return robotId;
    }

    public void setRobotId(String robotId) {
        this.robotId = robotId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public VideoChannel getChannel() {
        return channel;
    }

    public void setChannel(VideoChannel channel) {
        this.channel = channel;
    }

    public VideoQuality getQuality() {
        return quality;
    }

    public void setQuality(VideoQuality quality) {
        this.quality = quality;
    }

    public String getRoomName() {
        return roomName;
    }

    public void setRoomName(String roomName) {
        this.roomName = roomName;
    }

    public VideoSessionStatus getStatus() {
        return status;
    }

    public void setStatus(VideoSessionStatus status) {
        this.status = status;
    }

    public int getViewerCount() {
        return viewerCount;
    }

    public void setViewerCount(int viewerCount) {
        this.viewerCount = viewerCount;
    }

    public IntercomStatus getIntercomStatus() {
        return intercomStatus == null ? IntercomStatus.IDLE : intercomStatus;
    }

    public void setIntercomStatus(IntercomStatus intercomStatus) {
        this.intercomStatus = intercomStatus;
    }

    public boolean isIntercomAudioOnly() {
        return intercomAudioOnly;
    }

    public void setIntercomAudioOnly(boolean intercomAudioOnly) {
        this.intercomAudioOnly = intercomAudioOnly;
    }

    public String getIntercomOperatorId() {
        return intercomOperatorId;
    }

    public void setIntercomOperatorId(String intercomOperatorId) {
        this.intercomOperatorId = intercomOperatorId;
    }

    public String getIntercomClientId() {
        return intercomClientId;
    }

    public void setIntercomClientId(String intercomClientId) {
        this.intercomClientId = intercomClientId;
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

    public OffsetDateTime getIntercomStartedAt() {
        return intercomStartedAt;
    }

    public void setIntercomStartedAt(OffsetDateTime intercomStartedAt) {
        this.intercomStartedAt = intercomStartedAt;
    }

    public OffsetDateTime getIntercomHeartbeatAt() {
        return intercomHeartbeatAt;
    }

    public void setIntercomHeartbeatAt(OffsetDateTime intercomHeartbeatAt) {
        this.intercomHeartbeatAt = intercomHeartbeatAt;
    }

    public String getTrackSid() {
        return trackSid;
    }

    public void setTrackSid(String trackSid) {
        this.trackSid = trackSid;
    }

    public String getTrackName() {
        return trackName;
    }

    public void setTrackName(String trackName) {
        this.trackName = trackName;
    }

    public String getCommandId() {
        return commandId;
    }

    public void setCommandId(String commandId) {
        this.commandId = commandId;
    }

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(OffsetDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public OffsetDateTime getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(OffsetDateTime endedAt) {
        this.endedAt = endedAt;
    }

    public OffsetDateTime getCommandRequestedAt() {
        return commandRequestedAt;
    }

    public void setCommandRequestedAt(OffsetDateTime commandRequestedAt) {
        this.commandRequestedAt = commandRequestedAt;
    }

    public OffsetDateTime getLastStatusAt() {
        return lastStatusAt;
    }

    public void setLastStatusAt(OffsetDateTime lastStatusAt) {
        this.lastStatusAt = lastStatusAt;
    }

    public OffsetDateTime getIdleSince() {
        return idleSince;
    }

    public void setIdleSince(OffsetDateTime idleSince) {
        this.idleSince = idleSince;
    }

    public String getLastErrorCode() {
        return lastErrorCode;
    }

    public void setLastErrorCode(String lastErrorCode) {
        this.lastErrorCode = lastErrorCode;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public void setLastErrorMessage(String lastErrorMessage) {
        this.lastErrorMessage = lastErrorMessage;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
