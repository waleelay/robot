package com.robot.mediaserver.video.model;

import com.robot.media.common.video.VideoChannel;
import com.robot.media.common.video.VideoQuality;
import com.robot.media.common.video.VideoPublisherMode;
import com.robot.media.common.video.VideoSourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;

/**
 * 单个媒体源的唯一运行态记录。
 *
 * <p>用于串行化同源会话创建、固定房间所有权，并保存发布模式、操作版本号
 * 以及 LiveKit 返回的当前 Publisher/Track 事实。</p>
 */
@Entity
@Table(
        name = "media_source_runtime",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_source_runtime_source",
                    columnNames = {"source_type", "source_id", "device_id", "channel", "quality"}),
            @UniqueConstraint(name = "uk_source_runtime_ingress_id", columnNames = "ingress_id")
        })
public class VideoSourceRuntime {

    /**
     * 视频源运行实例 ID，供多个观看会话共享发布状态。
     */
    @Id
    @Column(name = "runtime_id", length = 64)
    private String runtimeId;

    /**
     * 视频来源类型。
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    private VideoSourceType sourceType;

    /**
     * 归一化后的视频源标识，与来源类型共同定位发布资源
     */
    @Column(name = "source_id", nullable = false, length = 64)
    private String sourceId;

    /**
     * 设备 ID。
     */
    @Column(name = "device_id", nullable = false, length = 64)
    private String deviceId;

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
    @Column(name = "room_name", nullable = false, length = 160)
    private String roomName;

    /**
     * 当前发布模式。
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "publisher_mode", nullable = false, length = 32)
    private VideoPublisherMode publisherMode;

    /**
     * 发布模式版本，用于拒绝陈旧发布者操作。
     */
    @Column(name = "publisher_revision", nullable = false)
    private long publisherRevision;

    /**
     * Ingress 管理操作版本。
     */
    @Column(name = "ingress_operation_revision", nullable = false)
    private long ingressOperationRevision;

    /**
     * 最近已接受的 Ingress 管理操作，用于识别同版本重复请求。
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "accepted_ingress_operation", length = 16)
    private FixedCameraIngressOperation acceptedIngressOperation;

    /**
     * LiveKit 接入资源标识。
     */
    @Column(name = "ingress_id", length = 128)
    private String ingressId;

    /**
     * 最近确认的固定摄像头流状态。
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "last_stream_status", length = 16)
    private FixedCameraStreamStatus lastStreamStatus;

    /**
     * 最近状态变化或失败的原因编码。
     */
    @Column(name = "last_reason_code", length = 64)
    private String lastReasonCode;

    /**
     * 最近一次向 LiveKit 核验发布事实的时间。
     */
    @Column(name = "last_verified_at")
    private OffsetDateTime lastVerifiedAt;

    /**
     * 发布端身份。
     */
    @Column(name = "publisher_identity", length = 128)
    private String publisherIdentity;

    /**
     * 当前发布者的 LiveKit Participant SID。
     */
    @Column(name = "publisher_participant_sid", length = 128)
    private String publisherParticipantSid;

    /**
     * LiveKit 轨道标识。
     */
    @Column(name = "track_sid", length = 128)
    private String trackSid;

    /**
     * 媒体轨道名称。
     */
    @Column(name = "track_name", length = 128)
    private String trackName;

    /**
     * 最近一次媒体活动时间。
     */
    @Column(name = "last_media_at")
    private OffsetDateTime lastMediaAt;

    /**
     * JPA 乐观锁版本，由持久化框架维护
     */
    @Version
    @Column(nullable = false)
    private long version;

    /**
     * 创建时间。
     */
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    /**
     * 更新时间。
     */
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

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
     * 读取{@link #sourceType}。
     *
     * @return 当前值，含义与约束见{@link #sourceType}
     */
    public VideoSourceType getSourceType() {
        return sourceType;
    }

    /**
     * 更新{@link #sourceType}。
     *
     * @param sourceType 新值，含义与约束见{@link #sourceType}
     */
    public void setSourceType(VideoSourceType sourceType) {
        this.sourceType = sourceType;
    }

    /**
     * 读取{@link #sourceId}。
     *
     * @return 当前值，含义与约束见{@link #sourceId}
     */
    public String getSourceId() {
        return sourceId;
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
     * 读取{@link #publisherMode}。
     *
     * @return 当前值，含义与约束见{@link #publisherMode}
     */
    public VideoPublisherMode getPublisherMode() {
        return publisherMode;
    }

    /**
     * 更新{@link #publisherMode}。
     *
     * @param publisherMode 新值，含义与约束见{@link #publisherMode}
     */
    public void setPublisherMode(VideoPublisherMode publisherMode) {
        this.publisherMode = publisherMode;
    }

    /**
     * 读取{@link #publisherRevision}。
     *
     * @return 当前值，含义与约束见{@link #publisherRevision}
     */
    public long getPublisherRevision() {
        return publisherRevision;
    }

    /**
     * 更新{@link #publisherRevision}。
     *
     * @param publisherRevision 新值，含义与约束见{@link #publisherRevision}
     */
    public void setPublisherRevision(long publisherRevision) {
        this.publisherRevision = publisherRevision;
    }

    /**
     * 读取{@link #ingressOperationRevision}。
     *
     * @return 当前值，含义与约束见{@link #ingressOperationRevision}
     */
    public long getIngressOperationRevision() {
        return ingressOperationRevision;
    }

    /**
     * 更新{@link #ingressOperationRevision}。
     *
     * @param ingressOperationRevision 新值，含义与约束见{@link #ingressOperationRevision}
     */
    public void setIngressOperationRevision(long ingressOperationRevision) {
        this.ingressOperationRevision = ingressOperationRevision;
    }

    /**
     * 读取{@link #acceptedIngressOperation}。
     *
     * @return 当前值，含义与约束见{@link #acceptedIngressOperation}
     */
    public FixedCameraIngressOperation getAcceptedIngressOperation() {
        return acceptedIngressOperation;
    }

    /**
     * 更新{@link #acceptedIngressOperation}。
     *
     * @param acceptedIngressOperation 新值，含义与约束见{@link #acceptedIngressOperation}
     */
    public void setAcceptedIngressOperation(FixedCameraIngressOperation acceptedIngressOperation) {
        this.acceptedIngressOperation = acceptedIngressOperation;
    }

    /**
     * 读取{@link #ingressId}。
     *
     * @return 当前值，含义与约束见{@link #ingressId}
     */
    public String getIngressId() {
        return ingressId;
    }

    /**
     * 更新{@link #ingressId}。
     *
     * @param ingressId 新值，含义与约束见{@link #ingressId}
     */
    public void setIngressId(String ingressId) {
        this.ingressId = ingressId;
    }

    /**
     * 读取{@link #lastStreamStatus}。
     *
     * @return 当前值，含义与约束见{@link #lastStreamStatus}
     */
    public FixedCameraStreamStatus getLastStreamStatus() {
        return lastStreamStatus;
    }

    /**
     * 更新{@link #lastStreamStatus}。
     *
     * @param lastStreamStatus 新值，含义与约束见{@link #lastStreamStatus}
     */
    public void setLastStreamStatus(FixedCameraStreamStatus lastStreamStatus) {
        this.lastStreamStatus = lastStreamStatus;
    }

    /**
     * 读取{@link #lastReasonCode}。
     *
     * @return 当前值，含义与约束见{@link #lastReasonCode}
     */
    public String getLastReasonCode() {
        return lastReasonCode;
    }

    /**
     * 更新{@link #lastReasonCode}。
     *
     * @param lastReasonCode 新值，含义与约束见{@link #lastReasonCode}
     */
    public void setLastReasonCode(String lastReasonCode) {
        this.lastReasonCode = lastReasonCode;
    }

    /**
     * 读取{@link #lastVerifiedAt}。
     *
     * @return 当前值，含义与约束见{@link #lastVerifiedAt}
     */
    public OffsetDateTime getLastVerifiedAt() {
        return lastVerifiedAt;
    }

    /**
     * 更新{@link #lastVerifiedAt}。
     *
     * @param lastVerifiedAt 新值，含义与约束见{@link #lastVerifiedAt}
     */
    public void setLastVerifiedAt(OffsetDateTime lastVerifiedAt) {
        this.lastVerifiedAt = lastVerifiedAt;
    }

    /**
     * 读取{@link #publisherIdentity}。
     *
     * @return 当前值，含义与约束见{@link #publisherIdentity}
     */
    public String getPublisherIdentity() {
        return publisherIdentity;
    }

    /**
     * 更新{@link #publisherIdentity}。
     *
     * @param publisherIdentity 新值，含义与约束见{@link #publisherIdentity}
     */
    public void setPublisherIdentity(String publisherIdentity) {
        this.publisherIdentity = publisherIdentity;
    }

    /**
     * 读取{@link #publisherParticipantSid}。
     *
     * @return 当前值，含义与约束见{@link #publisherParticipantSid}
     */
    public String getPublisherParticipantSid() {
        return publisherParticipantSid;
    }

    /**
     * 更新{@link #publisherParticipantSid}。
     *
     * @param publisherParticipantSid 新值，含义与约束见{@link #publisherParticipantSid}
     */
    public void setPublisherParticipantSid(String publisherParticipantSid) {
        this.publisherParticipantSid = publisherParticipantSid;
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
     * 读取{@link #lastMediaAt}。
     *
     * @return 当前值，含义与约束见{@link #lastMediaAt}
     */
    public OffsetDateTime getLastMediaAt() {
        return lastMediaAt;
    }

    /**
     * 更新{@link #lastMediaAt}。
     *
     * @param lastMediaAt 新值，含义与约束见{@link #lastMediaAt}
     */
    public void setLastMediaAt(OffsetDateTime lastMediaAt) {
        this.lastMediaAt = lastMediaAt;
    }

    /**
     * 读取{@link #version}。
     *
     * @return 当前值，含义与约束见{@link #version}
     */
    public long getVersion() {
        return version;
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
