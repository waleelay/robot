package com.robot.mediaserver.video.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;

/** 持久化观看者身份、租约和心跳，供视频会话复用与回收判断。 */
@Entity
@Table(
        name = "media_session_viewer",
        indexes = {
                @Index(name = "idx_session_viewer_session", columnList = "sessionId,leftAt"),
                @Index(name = "idx_session_viewer_user", columnList = "userId,joinedAt")
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uk_session_viewer_active_lease", columnNames = {"sessionId", "activeLeaseKey"}))
public class MediaSessionViewer {

    /**
     * 当前业务记录的唯一标识。
     */
    @Id
    @Column(length = 64)
    private String id;

    /**
     * 会话 ID。
     */
    @Column(nullable = false, length = 64)
    private String sessionId;

    /**
     * 用户 ID。
     */
    @Column(nullable = false, length = 64)
    private String userId;

    /**
     * 组织 ID。
     */
    @Column(length = 64)
    private String orgId;

    /**
     * LiveKit 参与者身份。
     */
    @Column(length = 128)
    private String participantIdentity;

    /**
     * 有效观看租约的唯一键，用于避免重复占位。
     */
    @Column(length = 128)
    private String activeLeaseKey;

    /**
     * 客户端 ID。
     */
    @Column(length = 128)
    private String clientId;

    /**
     * 客户端类型标识。
     */
    @Column(length = 32)
    private String clientType;

    /**
     * 观看者加入会话的时间。
     */
    private OffsetDateTime joinedAt;
    /**
     * 最后心跳时间。
     */
    private OffsetDateTime lastHeartbeatAt;
    /**
     * 观看者离开时间；为空表示租约尚未主动关闭。
     */
    private OffsetDateTime leftAt;

    /**
     * 读取{@link #id}。
     *
     * @return 当前值，含义与约束见{@link #id}
     */
    public String getId() {
        return id;
    }

    /**
     * 更新{@link #id}。
     *
     * @param id 新值，含义与约束见{@link #id}
     */
    public void setId(String id) {
        this.id = id;
    }

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
     * 读取{@link #userId}。
     *
     * @return 当前值，含义与约束见{@link #userId}
     */
    public String getUserId() {
        return userId;
    }

    /**
     * 更新{@link #userId}。
     *
     * @param userId 新值，含义与约束见{@link #userId}
     */
    public void setUserId(String userId) {
        this.userId = userId;
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
     * 读取{@link #participantIdentity}。
     *
     * @return 当前值，含义与约束见{@link #participantIdentity}
     */
    public String getParticipantIdentity() {
        return participantIdentity;
    }

    /**
     * 更新{@link #participantIdentity}。
     *
     * @param participantIdentity 新值，含义与约束见{@link #participantIdentity}
     */
    public void setParticipantIdentity(String participantIdentity) {
        this.participantIdentity = participantIdentity;
    }

    /**
     * 读取{@link #activeLeaseKey}。
     *
     * @return 当前值，含义与约束见{@link #activeLeaseKey}
     */
    public String getActiveLeaseKey() {
        return activeLeaseKey;
    }

    /**
     * 更新{@link #activeLeaseKey}。
     *
     * @param activeLeaseKey 新值，含义与约束见{@link #activeLeaseKey}
     */
    public void setActiveLeaseKey(String activeLeaseKey) {
        this.activeLeaseKey = activeLeaseKey;
    }

    /**
     * 读取{@link #clientId}。
     *
     * @return 当前值，含义与约束见{@link #clientId}
     */
    public String getClientId() {
        return clientId;
    }

    /**
     * 更新{@link #clientId}。
     *
     * @param clientId 新值，含义与约束见{@link #clientId}
     */
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    /**
     * 读取{@link #clientType}。
     *
     * @return 当前值，含义与约束见{@link #clientType}
     */
    public String getClientType() {
        return clientType;
    }

    /**
     * 更新{@link #clientType}。
     *
     * @param clientType 新值，含义与约束见{@link #clientType}
     */
    public void setClientType(String clientType) {
        this.clientType = clientType;
    }

    /**
     * 读取{@link #joinedAt}。
     *
     * @return 当前值，含义与约束见{@link #joinedAt}
     */
    public OffsetDateTime getJoinedAt() {
        return joinedAt;
    }

    /**
     * 更新{@link #joinedAt}。
     *
     * @param joinedAt 新值，含义与约束见{@link #joinedAt}
     */
    public void setJoinedAt(OffsetDateTime joinedAt) {
        this.joinedAt = joinedAt;
    }

    /**
     * 读取{@link #lastHeartbeatAt}。
     *
     * @return 当前值，含义与约束见{@link #lastHeartbeatAt}
     */
    public OffsetDateTime getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    /**
     * 更新{@link #lastHeartbeatAt}。
     *
     * @param lastHeartbeatAt 新值，含义与约束见{@link #lastHeartbeatAt}
     */
    public void setLastHeartbeatAt(OffsetDateTime lastHeartbeatAt) {
        this.lastHeartbeatAt = lastHeartbeatAt;
    }

    /**
     * 读取{@link #leftAt}。
     *
     * @return 当前值，含义与约束见{@link #leftAt}
     */
    public OffsetDateTime getLeftAt() {
        return leftAt;
    }

    /**
     * 更新{@link #leftAt}。
     *
     * @param leftAt 新值，含义与约束见{@link #leftAt}
     */
    public void setLeftAt(OffsetDateTime leftAt) {
        this.leftAt = leftAt;
    }
}
