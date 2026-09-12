package com.robot.mediaserver.video.repository;

import com.robot.mediaserver.video.model.MediaSessionViewer;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface MediaSessionViewerRepository extends JpaRepository<MediaSessionViewer, String> {

    @Modifying
    @Query(value = """
            insert into media_session_viewer (
                id, session_id, user_id, org_id, participant_identity, active_lease_key,
                client_id, client_type, joined_at, last_heartbeat_at
            ) values (
                :viewerId, :sessionId, :userId, :orgId, :identity, :identity,
                :clientId, 'web', :heartbeatAt, :heartbeatAt
            ) on duplicate key update
                user_id = values(user_id),
                org_id = values(org_id),
                client_id = values(client_id),
                last_heartbeat_at = values(last_heartbeat_at)
            """, nativeQuery = true)
    int upsertActiveLease(
            @Param("viewerId") String viewerId,
            @Param("sessionId") String sessionId,
            @Param("userId") String userId,
            @Param("orgId") String orgId,
            @Param("identity") String identity,
            @Param("clientId") String clientId,
            @Param("heartbeatAt") OffsetDateTime heartbeatAt);

    long countBySessionIdAndLeftAtIsNull(String sessionId);

    List<MediaSessionViewer> findByLeftAtIsNullAndLastHeartbeatAtBefore(OffsetDateTime lastHeartbeatAt);

    @Modifying
    @Query("""
            update MediaSessionViewer viewer
               set viewer.leftAt = :leftAt, viewer.activeLeaseKey = null
             where viewer.sessionId = :sessionId
               and viewer.participantIdentity = :identity
               and viewer.leftAt is null
            """)
    int closeActiveLease(
            @Param("sessionId") String sessionId,
            @Param("identity") String identity,
            @Param("leftAt") OffsetDateTime leftAt);

    @Modifying
    @Transactional
    @Query("""
            update MediaSessionViewer viewer
               set viewer.leftAt = :leftAt, viewer.activeLeaseKey = null
             where viewer.id = :viewerId
               and viewer.leftAt is null
               and viewer.lastHeartbeatAt < :heartbeatBefore
            """)
    int closeIfStale(
            @Param("viewerId") String viewerId,
            @Param("heartbeatBefore") OffsetDateTime heartbeatBefore,
            @Param("leftAt") OffsetDateTime leftAt);

}
