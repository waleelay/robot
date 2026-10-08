package com.robot.mediaserver.video.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import com.robot.media.common.video.IntercomStatus;
import com.robot.media.common.video.VideoChannel;
import com.robot.media.common.video.VideoQuality;
import com.robot.media.common.video.VideoSessionStatus;
import com.robot.media.common.video.VideoSourceType;


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
                @Index(name = "idx_video_session_source_status", columnList = "sourceType,sourceId,deviceId,channel,status"),
                @Index(name = "idx_video_session_runtime_status", columnList = "runtime_id,status"),
                @Index(name = "idx_video_session_created_by", columnList = "createdBy,createdAt")
        })
public class VideoSession {

    /**
     * 会话 ID。
     */
    @Id
    @Column(length = 64)
    private String sessionId;

    /**
     * 机器人 ID。
     */
    @Column(nullable = false, length = 64)
    private String robotId;

    /**
     * 设备 ID。
     */
    @Column(nullable = false, length = 64)
    private String deviceId;

    /**
     * 持久化的视频源类型；旧记录为空时按 ROBOT_CAMERA 读取。
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private VideoSourceType sourceType = VideoSourceType.ROBOT_CAMERA;

    /**
     * 持久化的视频源 ID；旧记录为空或空白时访问器回退到 robotId。
     */
    @Column(length = 64)
    private String sourceId;

    /**
     * 视频源运行实例 ID，供多个观看会话共享发布状态。
     */
    @Column(name = "runtime_id", length = 64)
    private String runtimeId;

    /**
     * 视频通道。
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private VideoChannel channel;

    /**
     * 视频清晰度。
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private VideoQuality quality;

    /**
     * LiveKit 房间名。
     */
    @Column(nullable = false, length = 160)
    private String roomName;

    /**
     * 媒体业务会话的生命周期状态；设备上报和 LiveKit 媒体事实共同驱动转换。
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private VideoSessionStatus status;

    /**
     * 观看人数。
     */
    @Column(nullable = false)
    private int viewerCount;

    /**
     * 有效对讲状态。
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 24)
    private IntercomStatus intercomStatus = IntercomStatus.IDLE;

    /**
     * 是否仅对讲音频。
     */
    @Column(nullable = false)
    private boolean intercomAudioOnly;

    /**
     * 对讲操作员 ID。
     */
    @Column(length = 64)
    private String intercomOperatorId;

    /**
     * 对讲客户端 ID。
     */
    @Column(length = 128)
    private String intercomClientId;

    /**
     * 当前对讲中记录的机器人麦克风音轨 SID，关闭对讲时清空。
     */
    @Column(length = 128)
    private String robotAudioTrackSid;

    /**
     * 机器人音频轨道名称。
     */
    @Column(length = 128)
    private String robotAudioTrackName;

    /**
     * 本轮对讲开始时间。
     */
    private OffsetDateTime intercomStartedAt;
    /**
     * 对讲操作端最近心跳时间。
     */
    private OffsetDateTime intercomHeartbeatAt;

    /**
     * LiveKit 轨道标识。
     */
    @Column(length = 128)
    private String trackSid;

    /**
     * 媒体轨道名称。
     */
    @Column(length = 128)
    private String trackName;

    /**
     * 指令 ID，用于关联客户端状态。
     */
    @Column(length = 64)
    private String commandId;

    /**
     * 组织 ID。
     */
    @Column(length = 64)
    private String orgId;

    /**
     * 创建该记录的用户标识。
     */
    @Column(length = 64)
    private String createdBy;

    /**
     * 开始时间。
     */
    private OffsetDateTime startedAt;
    /**
     * 结束时间。
     */
    private OffsetDateTime endedAt;
    /**
     * 最近请求发布媒体指令的时间。
     */
    private OffsetDateTime commandRequestedAt;
    /**
     * 最近接受视频状态上报的时间。
     */
    private OffsetDateTime lastStatusAt;
    /**
     * 会话开始无人观看的时间，用于延迟回收。
     */
    private OffsetDateTime idleSince;

    /**
     * 最后错误码。
     */
    @Column(length = 64)
    private String lastErrorCode;

    /**
     * 最后错误消息。
     */
    @Column(length = 512)
    private String lastErrorMessage;

    /**
     * 创建时间。
     */
    private OffsetDateTime createdAt;
    /**
     * 更新时间。
     */
    private OffsetDateTime updatedAt;

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
     * 读取{@link #robotId}。
     *
     * @return 当前值，含义与约束见{@link #robotId}
     */
    public String getRobotId() {
        return robotId;
    }

    /**
     * 更新{@link #robotId}。
     *
     * @param robotId 新值，含义与约束见{@link #robotId}
     */
    public void setRobotId(String robotId) {
        this.robotId = robotId;
    }

    /**
     * 读取{@link #deviceId}。
     *
     * @return 当前值，含义与约束见{@link #deviceId}
     */
    public String getDeviceId() {
        return deviceId;
    }

    /**
     * 更新{@link #deviceId}。
     *
     * @param deviceId 新值，含义与约束见{@link #deviceId}
     */
    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    /**
     * 读取视频源类型；兼容旧记录缺省值为机器人摄像头。
     *
     * @return 有效视频源类型
     */
    public VideoSourceType getSourceType() {
        return sourceType == null ? VideoSourceType.ROBOT_CAMERA : sourceType;
    }

    /**
     * 保存视频源类型；传入 null 时保持旧协议的机器人摄像头默认值。
     *
     * @param sourceType 视频来源类型
     */
    public void setSourceType(VideoSourceType sourceType) {
        this.sourceType = sourceType == null ? VideoSourceType.ROBOT_CAMERA : sourceType;
    }

    /**
     * 读取视频源标识；旧记录缺省时使用 robotId。
     *
     * @return 有效视频源标识
     */
    public String getSourceId() {
        return sourceId == null || sourceId.isBlank() ? robotId : sourceId;
    }

    /**
     * 更新{@link #sourceId}。
     *
     * @param sourceId 新值，含义与约束见{@link #sourceId}
     */
    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    /**
     * 读取{@link #runtimeId}。
     *
     * @return 当前值，含义与约束见{@link #runtimeId}
     */
    public String getRuntimeId() {
        return runtimeId;
    }

    /**
     * 更新{@link #runtimeId}。
     *
     * @param runtimeId 新值，含义与约束见{@link #runtimeId}
     */
    public void setRuntimeId(String runtimeId) {
        this.runtimeId = runtimeId;
    }

    /**
     * 读取{@link #channel}。
     *
     * @return 当前值，含义与约束见{@link #channel}
     */
    public VideoChannel getChannel() {
        return channel;
    }

    /**
     * 更新{@link #channel}。
     *
     * @param channel 新值，含义与约束见{@link #channel}
     */
    public void setChannel(VideoChannel channel) {
        this.channel = channel;
    }

    /**
     * 读取{@link #quality}。
     *
     * @return 当前值，含义与约束见{@link #quality}
     */
    public VideoQuality getQuality() {
        return quality;
    }

    /**
     * 更新{@link #quality}。
     *
     * @param quality 新值，含义与约束见{@link #quality}
     */
    public void setQuality(VideoQuality quality) {
        this.quality = quality;
    }

    /**
     * 读取{@link #roomName}。
     *
     * @return 当前值，含义与约束见{@link #roomName}
     */
    public String getRoomName() {
        return roomName;
    }

    /**
     * 更新{@link #roomName}。
     *
     * @param roomName 新值，含义与约束见{@link #roomName}
     */
    public void setRoomName(String roomName) {
        this.roomName = roomName;
    }

    /**
     * 读取{@link #status}。
     *
     * @return 当前值，含义与约束见{@link #status}
     */
    public VideoSessionStatus getStatus() {
        return status;
    }

    /**
     * 更新{@link #status}。
     *
     * @param status 新值，含义与约束见{@link #status}
     */
    public void setStatus(VideoSessionStatus status) {
        this.status = status;
    }

    /**
     * 读取{@link #viewerCount}。
     *
     * @return 当前值，含义与约束见{@link #viewerCount}
     */
    public int getViewerCount() {
        return viewerCount;
    }

    /**
     * 更新{@link #viewerCount}。
     *
     * @param viewerCount 新值，含义与约束见{@link #viewerCount}
     */
    public void setViewerCount(int viewerCount) {
        this.viewerCount = viewerCount;
    }

    /**
     * 读取对讲状态；兼容旧记录的空值为未占用。
     *
     * @return 有效对讲状态
     */
    public IntercomStatus getIntercomStatus() {
        return intercomStatus == null ? IntercomStatus.IDLE : intercomStatus;
    }

    /**
     * 更新{@link #intercomStatus}。
     *
     * @param intercomStatus 新值，含义与约束见{@link #intercomStatus}
     */
    public void setIntercomStatus(IntercomStatus intercomStatus) {
        this.intercomStatus = intercomStatus;
    }

    /**
     * 读取{@link #intercomAudioOnly}。
     *
     * @return 当前值，含义与约束见{@link #intercomAudioOnly}
     */
    public boolean isIntercomAudioOnly() {
        return intercomAudioOnly;
    }

    /**
     * 更新{@link #intercomAudioOnly}。
     *
     * @param intercomAudioOnly 新值，含义与约束见{@link #intercomAudioOnly}
     */
    public void setIntercomAudioOnly(boolean intercomAudioOnly) {
        this.intercomAudioOnly = intercomAudioOnly;
    }

    /**
     * 读取{@link #intercomOperatorId}。
     *
     * @return 当前值，含义与约束见{@link #intercomOperatorId}
     */
    public String getIntercomOperatorId() {
        return intercomOperatorId;
    }

    /**
     * 更新{@link #intercomOperatorId}。
     *
     * @param intercomOperatorId 新值，含义与约束见{@link #intercomOperatorId}
     */
    public void setIntercomOperatorId(String intercomOperatorId) {
        this.intercomOperatorId = intercomOperatorId;
    }

    /**
     * 读取{@link #intercomClientId}。
     *
     * @return 当前值，含义与约束见{@link #intercomClientId}
     */
    public String getIntercomClientId() {
        return intercomClientId;
    }

    /**
     * 更新{@link #intercomClientId}。
     *
     * @param intercomClientId 新值，含义与约束见{@link #intercomClientId}
     */
    public void setIntercomClientId(String intercomClientId) {
        this.intercomClientId = intercomClientId;
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
     * 读取{@link #intercomStartedAt}。
     *
     * @return 当前值，含义与约束见{@link #intercomStartedAt}
     */
    public OffsetDateTime getIntercomStartedAt() {
        return intercomStartedAt;
    }

    /**
     * 更新{@link #intercomStartedAt}。
     *
     * @param intercomStartedAt 新值，含义与约束见{@link #intercomStartedAt}
     */
    public void setIntercomStartedAt(OffsetDateTime intercomStartedAt) {
        this.intercomStartedAt = intercomStartedAt;
    }

    /**
     * 读取{@link #intercomHeartbeatAt}。
     *
     * @return 当前值，含义与约束见{@link #intercomHeartbeatAt}
     */
    public OffsetDateTime getIntercomHeartbeatAt() {
        return intercomHeartbeatAt;
    }

    /**
     * 更新{@link #intercomHeartbeatAt}。
     *
     * @param intercomHeartbeatAt 新值，含义与约束见{@link #intercomHeartbeatAt}
     */
    public void setIntercomHeartbeatAt(OffsetDateTime intercomHeartbeatAt) {
        this.intercomHeartbeatAt = intercomHeartbeatAt;
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
     * 读取{@link #commandId}。
     *
     * @return 当前值，含义与约束见{@link #commandId}
     */
    public String getCommandId() {
        return commandId;
    }

    /**
     * 更新{@link #commandId}。
     *
     * @param commandId 新值，含义与约束见{@link #commandId}
     */
    public void setCommandId(String commandId) {
        this.commandId = commandId;
    }

    /**
     * 读取{@link #orgId}。
     *
     * @return 当前值，含义与约束见{@link #orgId}
     */
    public String getOrgId() {
        return orgId;
    }

    /**
     * 更新{@link #orgId}。
     *
     * @param orgId 新值，含义与约束见{@link #orgId}
     */
    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    /**
     * 读取{@link #createdBy}。
     *
     * @return 当前值，含义与约束见{@link #createdBy}
     */
    public String getCreatedBy() {
        return createdBy;
    }

    /**
     * 更新{@link #createdBy}。
     *
     * @param createdBy 新值，含义与约束见{@link #createdBy}
     */
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    /**
     * 读取{@link #startedAt}。
     *
     * @return 当前值，含义与约束见{@link #startedAt}
     */
    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    /**
     * 更新{@link #startedAt}。
     *
     * @param startedAt 新值，含义与约束见{@link #startedAt}
     */
    public void setStartedAt(OffsetDateTime startedAt) {
        this.startedAt = startedAt;
    }

    /**
     * 读取{@link #endedAt}。
     *
     * @return 当前值，含义与约束见{@link #endedAt}
     */
    public OffsetDateTime getEndedAt() {
        return endedAt;
    }

    /**
     * 更新{@link #endedAt}。
     *
     * @param endedAt 新值，含义与约束见{@link #endedAt}
     */
    public void setEndedAt(OffsetDateTime endedAt) {
        this.endedAt = endedAt;
    }

    /**
     * 读取{@link #commandRequestedAt}。
     *
     * @return 当前值，含义与约束见{@link #commandRequestedAt}
     */
    public OffsetDateTime getCommandRequestedAt() {
        return commandRequestedAt;
    }

    /**
     * 更新{@link #commandRequestedAt}。
     *
     * @param commandRequestedAt 新值，含义与约束见{@link #commandRequestedAt}
     */
    public void setCommandRequestedAt(OffsetDateTime commandRequestedAt) {
        this.commandRequestedAt = commandRequestedAt;
    }

    /**
     * 读取{@link #lastStatusAt}。
     *
     * @return 当前值，含义与约束见{@link #lastStatusAt}
     */
    public OffsetDateTime getLastStatusAt() {
        return lastStatusAt;
    }

    /**
     * 更新{@link #lastStatusAt}。
     *
     * @param lastStatusAt 新值，含义与约束见{@link #lastStatusAt}
     */
    public void setLastStatusAt(OffsetDateTime lastStatusAt) {
        this.lastStatusAt = lastStatusAt;
    }

    /**
     * 读取{@link #idleSince}。
     *
     * @return 当前值，含义与约束见{@link #idleSince}
     */
    public OffsetDateTime getIdleSince() {
        return idleSince;
    }

    /**
     * 更新{@link #idleSince}。
     *
     * @param idleSince 新值，含义与约束见{@link #idleSince}
     */
    public void setIdleSince(OffsetDateTime idleSince) {
        this.idleSince = idleSince;
    }

    /**
     * 读取{@link #lastErrorCode}。
     *
     * @return 当前值，含义与约束见{@link #lastErrorCode}
     */
    public String getLastErrorCode() {
        return lastErrorCode;
    }

    /**
     * 更新{@link #lastErrorCode}。
     *
     * @param lastErrorCode 新值，含义与约束见{@link #lastErrorCode}
     */
    public void setLastErrorCode(String lastErrorCode) {
        this.lastErrorCode = lastErrorCode;
    }

    /**
     * 读取{@link #lastErrorMessage}。
     *
     * @return 当前值，含义与约束见{@link #lastErrorMessage}
     */
    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    /**
     * 更新{@link #lastErrorMessage}。
     *
     * @param lastErrorMessage 新值，含义与约束见{@link #lastErrorMessage}
     */
    public void setLastErrorMessage(String lastErrorMessage) {
        this.lastErrorMessage = lastErrorMessage;
    }

    /**
     * 读取{@link #createdAt}。
     *
     * @return 当前值，含义与约束见{@link #createdAt}
     */
    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * 更新{@link #createdAt}。
     *
     * @param createdAt 新值，含义与约束见{@link #createdAt}
     */
    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * 读取{@link #updatedAt}。
     *
     * @return 当前值，含义与约束见{@link #updatedAt}
     */
    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    /**
     * 更新{@link #updatedAt}。
     *
     * @param updatedAt 新值，含义与约束见{@link #updatedAt}
     */
    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
