package com.robot.mediaserver.video.repository;

import com.robot.media.common.video.VideoChannel;
import com.robot.media.common.video.VideoQuality;
import com.robot.mediaserver.video.model.VideoSession;
import com.robot.media.common.video.VideoSessionStatus;
import com.robot.media.common.video.IntercomStatus;
import com.robot.media.common.video.VideoSourceType;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 实时视频会话仓储。
 *
 * @author leelay
 * @date 2026/05/19
 */
public interface VideoSessionRepository extends JpaRepository<VideoSession, String> {

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

        List<VideoSession> findByStatusAndUpdatedAtBefore(VideoSessionStatus status, OffsetDateTime updatedAt);

        List<VideoSession> findByStatusAndSourceTypeAndUpdatedAtBefore(
            VideoSessionStatus status,
            VideoSourceType sourceType,
            OffsetDateTime updatedAt);

        List<VideoSession> findByStatusAndLastStatusAtBefore(VideoSessionStatus status, OffsetDateTime lastStatusAt);

        List<VideoSession> findByStatusAndIdleSinceBefore(VideoSessionStatus status, OffsetDateTime idleSince);

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
