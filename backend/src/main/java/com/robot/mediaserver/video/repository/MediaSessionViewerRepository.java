package com.robot.mediaserver.video.repository;

import com.robot.mediaserver.video.model.MediaSessionViewer;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface MediaSessionViewerRepository extends JpaRepository<MediaSessionViewer, String> {

    Optional<MediaSessionViewer> findFirstBySessionIdAndParticipantIdentityAndLeftAtIsNull(String sessionId, String participantIdentity);

    long countBySessionIdAndLeftAtIsNull(String sessionId);

    List<MediaSessionViewer> findByLeftAtIsNullAndLastHeartbeatAtBefore(OffsetDateTime lastHeartbeatAt);

    @Modifying
    @Transactional
    @Query("""
            update MediaSessionViewer viewer
               set viewer.leftAt = :leftAt
             where viewer.id = :viewerId
               and viewer.leftAt is null
               and viewer.lastHeartbeatAt < :heartbeatBefore
            """)
    int closeIfStale(
            @Param("viewerId") String viewerId,
            @Param("heartbeatBefore") OffsetDateTime heartbeatBefore,
            @Param("leftAt") OffsetDateTime leftAt);

}
