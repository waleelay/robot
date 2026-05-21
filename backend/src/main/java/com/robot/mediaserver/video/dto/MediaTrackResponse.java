package com.robot.mediaserver.video.dto;

import com.robot.mediaserver.video.model.MediaTrack;
import com.robot.mediaserver.video.model.VideoChannel;
import com.robot.mediaserver.video.model.VideoQuality;
import java.time.OffsetDateTime;

public record MediaTrackResponse(
        String trackId,
        String sessionId,
        String trackSid,
        String trackName,
        String participantIdentity,
        String kind,
        VideoChannel channel,
        VideoQuality quality,
        OffsetDateTime publishedAt,
        OffsetDateTime unpublishedAt) {

    public static MediaTrackResponse from(MediaTrack track) {
        return new MediaTrackResponse(
                track.getTrackId(),
                track.getSessionId(),
                track.getTrackSid(),
                track.getTrackName(),
                track.getParticipantIdentity(),
                track.getKind(),
                track.getChannel(),
                track.getQuality(),
                track.getPublishedAt(),
                track.getUnpublishedAt());
    }
}
