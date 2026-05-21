package com.robot.mediaserver.video.service;

import com.robot.mediaserver.video.dto.MediaTrackResponse;
import com.robot.mediaserver.video.model.MediaTrack;
import com.robot.mediaserver.video.model.VideoSession;
import com.robot.mediaserver.video.repository.MediaTrackRepository;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class MediaTrackService {

    private final MediaTrackRepository repository;

    public MediaTrackService(MediaTrackRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void publish(VideoSession session, String trackSid, String trackName) {
        if (trackSid == null || trackSid.isBlank()) {
            return;
        }
        repository.findFirstBySessionIdAndTrackSidAndUnpublishedAtIsNull(session.getSessionId(), trackSid)
                .orElseGet(() -> {
                    MediaTrack track = new MediaTrack();
                    track.setTrackId("track_" + compactUuid());
                    track.setSessionId(session.getSessionId());
                    track.setTrackSid(trackSid);
                    track.setTrackName(trackName);
                    track.setParticipantIdentity("robot:" + session.getRobotId() + ":" + session.getDeviceId());
                    track.setKind("video");
                    track.setChannel(session.getChannel());
                    track.setQuality(session.getQuality());
                    track.setPublishedAt(now());
                    return repository.save(track);
                });
    }

    @Transactional
    public void unpublish(VideoSession session) {
        if (session.getTrackSid() == null || session.getTrackSid().isBlank()) {
            return;
        }
        repository.findFirstBySessionIdAndTrackSidAndUnpublishedAtIsNull(session.getSessionId(), session.getTrackSid())
                .ifPresent(track -> {
                    track.setUnpublishedAt(now());
                    repository.save(track);
                });
    }

    public List<MediaTrackResponse> recentBySession(String sessionId) {
        return repository.findTop20BySessionIdOrderByPublishedAtDesc(sessionId).stream()
                .map(MediaTrackResponse::from)
                .toList();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private String compactUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
