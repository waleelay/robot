package com.robot.mediaserver.video.repository;

import com.robot.mediaserver.video.model.MediaSessionViewer;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaSessionViewerRepository extends JpaRepository<MediaSessionViewer, String> {

    Optional<MediaSessionViewer> findFirstBySessionIdAndParticipantIdentityAndLeftAtIsNull(String sessionId, String participantIdentity);

    long countBySessionIdAndLeftAtIsNull(String sessionId);
}
