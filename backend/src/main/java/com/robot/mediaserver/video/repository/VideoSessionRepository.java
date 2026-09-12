package com.robot.mediaserver.video.repository;

import com.robot.media.common.video.VideoChannel;
import com.robot.media.common.video.VideoQuality;
import com.robot.mediaserver.video.model.VideoSession;
import com.robot.media.common.video.VideoSessionStatus;
import com.robot.media.common.video.IntercomStatus;
import com.robot.media.common.video.VideoSourceType;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 实时视频会话仓储。
 *
 * @author leelay
 * @date 2026/05/19
 */
public interface VideoSessionRepository extends JpaRepository<VideoSession, String> {

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select session from VideoSession session where session.sessionId = :sessionId")
        Optional<VideoSession> findByIdForUpdate(@Param("sessionId") String sessionId);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        List<VideoSession> findByRuntimeIdOrderBySessionIdAsc(String runtimeId);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        List<VideoSession> findByRuntimeIdIsNullAndSourceTypeAndSourceIdAndDeviceIdAndChannelAndQualityOrderBySessionIdAsc(
            VideoSourceType sourceType,
            String sourceId,
            String deviceId,
            VideoChannel channel,
            VideoQuality quality);

        Optional<VideoSession> findFirstByRobotIdAndDeviceIdAndChannelAndQualityAndStatusInOrderByCreatedAtDesc(
            String robotId,
            String deviceId,
            VideoChannel channel,
            VideoQuality quality,
            Collection<VideoSessionStatus> statuses);

        Optional<VideoSession> findFirstBySourceTypeAndSourceIdAndDeviceIdAndChannelAndQualityAndStatusInOrderByCreatedAtDesc(
            VideoSourceType sourceType,
            String sourceId,
            String deviceId,
            VideoChannel channel,
            VideoQuality quality,
            Collection<VideoSessionStatus> statuses);

        Optional<VideoSession> findFirstByRuntimeIdAndStatusInOrderByCreatedAtDesc(
            String runtimeId,
            Collection<VideoSessionStatus> statuses);

        List<VideoSession> findByStatusAndCommandRequestedAtBefore(
            VideoSessionStatus status,
            OffsetDateTime commandRequestedAt);

        List<VideoSession> findByStatusAndSourceTypeAndCommandRequestedAtBefore(
            VideoSessionStatus status,
            VideoSourceType sourceType,
            OffsetDateTime commandRequestedAt);

        List<VideoSession> findByStatusAndLastStatusAtBefore(VideoSessionStatus status, OffsetDateTime lastStatusAt);

        @Query("select distinct session.runtimeId from VideoSession session where session.runtimeId is not null and session.status in :statuses")
        List<String> findDistinctRuntimeIdsByStatusIn(@Param("statuses") Collection<VideoSessionStatus> statuses);

        List<VideoSession> findByStatusAndIdleSinceBefore(VideoSessionStatus status, OffsetDateTime idleSince);

        @Query("""
                select session.sessionId from VideoSession session
                where session.status in :statuses
                  and not exists (
                      select viewer.id from MediaSessionViewer viewer
                      where viewer.sessionId = session.sessionId
                        and viewer.leftAt is null
                  )
                order by session.updatedAt asc
                """)
        List<String> findUnoccupiedSessionIds(
                @Param("statuses") Collection<VideoSessionStatus> statuses,
                Pageable pageable);

    @Query("""
            select session from VideoSession session
            where session.intercomStatus in :statuses
              and (session.intercomHeartbeatAt is null or session.intercomHeartbeatAt < :heartbeatBefore)
            """)
    List<VideoSession> findIntercomTimeoutCandidates(
            @Param("statuses") Collection<IntercomStatus> statuses,
            @Param("heartbeatBefore") OffsetDateTime heartbeatBefore);

    List<VideoSession> findByIntercomStatusIn(Collection<IntercomStatus> statuses);

    List<VideoSession> findTop16ByStatusInOrderByUpdatedAtDesc(Collection<VideoSessionStatus> statuses);

    List<VideoSession> findByRobotIdAndViewerCountGreaterThanAndStatusInOrderByUpdatedAtDesc(
            String robotId,
            int viewerCount,
            Collection<VideoSessionStatus> statuses);

        List<VideoSession> findTop20ByCreatedByOrderByCreatedAtDesc(String createdBy);
}
