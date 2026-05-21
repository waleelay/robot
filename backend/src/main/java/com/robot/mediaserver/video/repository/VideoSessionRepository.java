package com.robot.mediaserver.video.repository;

import com.robot.mediaserver.video.model.VideoChannel;
import com.robot.mediaserver.video.model.VideoQuality;
import com.robot.mediaserver.video.model.VideoSession;
import com.robot.mediaserver.video.model.VideoSessionStatus;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

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

        List<VideoSession> findByStatusAndUpdatedAtBefore(VideoSessionStatus status, OffsetDateTime updatedAt);

        List<VideoSession> findByStatusAndIdleSinceBefore(VideoSessionStatus status, OffsetDateTime idleSince);

    List<VideoSession> findTop16ByStatusInOrderByUpdatedAtDesc(Collection<VideoSessionStatus> statuses);

    List<VideoSession> findByRobotIdAndViewerCountGreaterThanAndStatusInOrderByUpdatedAtDesc(
            String robotId,
            int viewerCount,
            Collection<VideoSessionStatus> statuses);

        List<VideoSession> findTop20ByCreatedByOrderByCreatedAtDesc(String createdBy);
}
