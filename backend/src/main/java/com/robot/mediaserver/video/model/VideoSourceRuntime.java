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
 * <p>用于串行化同源会话创建、固定 Room 所有权，并保存发布模式、操作 generation
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

    @Id
    @Column(name = "runtime_id", length = 64)
    private String runtimeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    private VideoSourceType sourceType;

    @Column(name = "source_id", nullable = false, length = 64)
    private String sourceId;

    @Column(name = "device_id", nullable = false, length = 64)
    private String deviceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private VideoChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private VideoQuality quality;

    @Column(name = "room_name", nullable = false, length = 160)
    private String roomName;

    @Enumerated(EnumType.STRING)
    @Column(name = "publisher_mode", nullable = false, length = 32)
    private VideoPublisherMode publisherMode;

    @Column(name = "publisher_revision", nullable = false)
    private long publisherRevision;

    @Column(name = "ingress_operation_revision", nullable = false)
    private long ingressOperationRevision;

    @Enumerated(EnumType.STRING)
    @Column(name = "accepted_ingress_operation", length = 16)
    private FixedCameraIngressOperation acceptedIngressOperation;

    @Column(name = "ingress_id", length = 128)
    private String ingressId;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_stream_status", length = 16)
    private FixedCameraStreamStatus lastStreamStatus;

    @Column(name = "last_reason_code", length = 64)
    private String lastReasonCode;

    @Column(name = "last_verified_at")
    private OffsetDateTime lastVerifiedAt;

    @Column(name = "publisher_identity", length = 128)
    private String publisherIdentity;

    @Column(name = "publisher_participant_sid", length = 128)
    private String publisherParticipantSid;

    @Column(name = "track_sid", length = 128)
    private String trackSid;

    @Column(name = "track_name", length = 128)
    private String trackName;

    @Column(name = "last_media_at")
    private OffsetDateTime lastMediaAt;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public String getRuntimeId() {
        return runtimeId;
    }

    public void setRuntimeId(String runtimeId) {
        this.runtimeId = runtimeId;
    }

    public VideoSourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(VideoSourceType sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
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

    public VideoPublisherMode getPublisherMode() {
        return publisherMode;
    }

    public void setPublisherMode(VideoPublisherMode publisherMode) {
        this.publisherMode = publisherMode;
    }

    public long getPublisherRevision() {
        return publisherRevision;
    }

    public void setPublisherRevision(long publisherRevision) {
        this.publisherRevision = publisherRevision;
    }

    public long getIngressOperationRevision() {
        return ingressOperationRevision;
    }

    public void setIngressOperationRevision(long ingressOperationRevision) {
        this.ingressOperationRevision = ingressOperationRevision;
    }

    public FixedCameraIngressOperation getAcceptedIngressOperation() {
        return acceptedIngressOperation;
    }

    public void setAcceptedIngressOperation(FixedCameraIngressOperation acceptedIngressOperation) {
        this.acceptedIngressOperation = acceptedIngressOperation;
    }

    public String getIngressId() {
        return ingressId;
    }

    public void setIngressId(String ingressId) {
        this.ingressId = ingressId;
    }

    public FixedCameraStreamStatus getLastStreamStatus() {
        return lastStreamStatus;
    }

    public void setLastStreamStatus(FixedCameraStreamStatus lastStreamStatus) {
        this.lastStreamStatus = lastStreamStatus;
    }

    public String getLastReasonCode() {
        return lastReasonCode;
    }

    public void setLastReasonCode(String lastReasonCode) {
        this.lastReasonCode = lastReasonCode;
    }

    public OffsetDateTime getLastVerifiedAt() {
        return lastVerifiedAt;
    }

    public void setLastVerifiedAt(OffsetDateTime lastVerifiedAt) {
        this.lastVerifiedAt = lastVerifiedAt;
    }

    public String getPublisherIdentity() {
        return publisherIdentity;
    }

    public void setPublisherIdentity(String publisherIdentity) {
        this.publisherIdentity = publisherIdentity;
    }

    public String getPublisherParticipantSid() {
        return publisherParticipantSid;
    }

    public void setPublisherParticipantSid(String publisherParticipantSid) {
        this.publisherParticipantSid = publisherParticipantSid;
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

    public OffsetDateTime getLastMediaAt() {
        return lastMediaAt;
    }

    public void setLastMediaAt(OffsetDateTime lastMediaAt) {
        this.lastMediaAt = lastMediaAt;
    }

    public long getVersion() {
        return version;
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
