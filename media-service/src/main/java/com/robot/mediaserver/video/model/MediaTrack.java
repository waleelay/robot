package com.robot.mediaserver.video.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import com.robot.media.common.video.VideoChannel;
import com.robot.media.common.video.VideoQuality;


/** 持久化媒体轨道的发布者、外部标识及发布和取消发布时间。 */
@Entity
@Table(
        name = "media_track",
        indexes = {
                @Index(name = "idx_media_track_session", columnList = "sessionId,publishedAt"),
                @Index(name = "idx_media_track_sid", columnList = "trackSid")
        })
public class MediaTrack {

    /**
     * 媒体轨道记录标识。
     */
    @Id
    @Column(length = 64)
    private String trackId;

    /**
     * 会话 ID。
     */
    @Column(nullable = false, length = 64)
    private String sessionId;

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
     * LiveKit 参与者身份。
     */
    @Column(length = 128)
    private String participantIdentity;

    /**
     * Track 类型。
     */
    @Column(length = 24)
    private String kind;

    /**
     * 视频通道。
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 24)
    private VideoChannel channel;

    /**
     * 视频清晰度。
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private VideoQuality quality;

    /**
     * 发布时间。
     */
    private OffsetDateTime publishedAt;
    /**
     * 取消发布时间。
     */
    private OffsetDateTime unpublishedAt;

    /**
     * 读取{@link #trackId}。
     *
     * @return 当前值，含义与约束见{@link #trackId}
     */
    public String getTrackId() {
        return trackId;
    }

    /**
     * 更新{@link #trackId}。
     *
     * @param trackId 新值，含义与约束见{@link #trackId}
     */
    public void setTrackId(String trackId) {
        this.trackId = trackId;
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
     * 读取{@link #kind}。
     *
     * @return 当前值，含义与约束见{@link #kind}
     */
    public String getKind() {
        return kind;
    }

    /**
     * 更新{@link #kind}。
     *
     * @param kind 新值，含义与约束见{@link #kind}
     */
    public void setKind(String kind) {
        this.kind = kind;
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
     * 读取{@link #publishedAt}。
     *
     * @return 当前值，含义与约束见{@link #publishedAt}
     */
    public OffsetDateTime getPublishedAt() {
        return publishedAt;
    }

    /**
     * 更新{@link #publishedAt}。
     *
     * @param publishedAt 新值，含义与约束见{@link #publishedAt}
     */
    public void setPublishedAt(OffsetDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    /**
     * 读取{@link #unpublishedAt}。
     *
     * @return 当前值，含义与约束见{@link #unpublishedAt}
     */
    public OffsetDateTime getUnpublishedAt() {
        return unpublishedAt;
    }

    /**
     * 更新{@link #unpublishedAt}。
     *
     * @param unpublishedAt 新值，含义与约束见{@link #unpublishedAt}
     */
    public void setUnpublishedAt(OffsetDateTime unpublishedAt) {
        this.unpublishedAt = unpublishedAt;
    }
}
