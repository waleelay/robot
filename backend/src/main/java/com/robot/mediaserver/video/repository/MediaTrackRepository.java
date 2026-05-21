package com.robot.mediaserver.video.repository;

import com.robot.mediaserver.video.model.MediaTrack;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaTrackRepository extends JpaRepository<MediaTrack, String> {

    Optional<MediaTrack> findFirstBySessionIdAndTrackSidAndUnpublishedAtIsNull(String sessionId, String trackSid);

    List<MediaTrack> findTop20BySessionIdOrderByPublishedAtDesc(String sessionId);
}
