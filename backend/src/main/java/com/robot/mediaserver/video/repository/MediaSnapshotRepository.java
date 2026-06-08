package com.robot.mediaserver.video.repository;

import com.robot.mediaserver.video.model.MediaSnapshot;
import com.robot.mediaserver.video.model.SnapshotStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 媒体抓拍仓储。
 *
 * @author leelay
 * @date 2026/05/20
 */
public interface MediaSnapshotRepository extends JpaRepository<MediaSnapshot, String> {

    /**
     * 查询指定会话最近抓拍记录。
     *
     * @param sessionId 会话 ID
     * @return 抓拍记录列表
     */
    List<MediaSnapshot> findTop20BySessionIdOrderByCreatedAtDesc(String sessionId);

    List<MediaSnapshot> findTop50ByRobotIdAndDeviceIdOrderByCreatedAtDesc(String robotId, String deviceId);

    List<MediaSnapshot> findTop10ByStatusOrderByCreatedAtAsc(SnapshotStatus status);
}
