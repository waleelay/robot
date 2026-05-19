package com.robot.mediaserver.video.repository;

import com.robot.mediaserver.video.model.VideoChannel;
import com.robot.mediaserver.video.model.VideoQuality;
import com.robot.mediaserver.video.model.VideoSession;
import com.robot.mediaserver.video.model.VideoSessionStatus;
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

    /**
     * 查询同一路视频是否存在可复用会话。
     *
     * @param robotId 机器人 ID
     * @param deviceId 设备 ID
     * @param channel 媒体通道
     * @param quality 清晰度
     * @param statuses 可复用状态集合
     * @return 最近一条可复用会话
     */
    Optional<VideoSession> findFirstByRobotIdAndDeviceIdAndChannelAndQualityAndStatusInOrderByCreatedAtDesc(
            String robotId,
            String deviceId,
            VideoChannel channel,
            VideoQuality quality,
            Collection<VideoSessionStatus> statuses);

    /**
     * 查询用户最近创建的实时视频会话。
     *
     * @param createdBy 创建人
     * @return 最近会话列表
     */
    List<VideoSession> findTop20ByCreatedByOrderByCreatedAtDesc(String createdBy);
}
