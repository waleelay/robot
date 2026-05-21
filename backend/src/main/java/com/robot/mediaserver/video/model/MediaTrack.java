package com.robot.mediaserver.video.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(
        name = "media_track",
        indexes = {
                @Index(name = "idx_media_track_session", columnList = "sessionId,publishedAt"),
                @Index(name = "idx_media_track_sid", columnList = "trackSid")
        })
public class MediaTrack {

    @Id
    @Column(length = 64)
    private String trackId;

    @Column(nullable = false, length = 64)
    private String sessionId;

    @Column(length = 128)
    private String trackSid;

    @Column(length = 128)
    private String trackName;

    @Column(length = 128)
    private String participantIdentity;

    @Column(length = 24)
    private String kind;

    @Enumerated(EnumType.STRING)
    @Column(length = 24)
    private VideoChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private VideoQuality quality;

    private OffsetDateTime publishedAt;
    private OffsetDateTime unpublishedAt;

    public String getTrackId() {
        return trackId;
    }

    public void setTrackId(String trackId) {
        this.trackId = trackId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
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

    public String getParticipantIdentity() {
        return participantIdentity;
    }

    public void setParticipantIdentity(String participantIdentity) {
        this.participantIdentity = participantIdentity;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
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

    public OffsetDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(OffsetDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public OffsetDateTime getUnpublishedAt() {
        return unpublishedAt;
    }

    public void setUnpublishedAt(OffsetDateTime unpublishedAt) {
        this.unpublishedAt = unpublishedAt;
    }
}
