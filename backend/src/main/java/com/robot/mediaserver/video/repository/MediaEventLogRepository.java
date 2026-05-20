package com.robot.mediaserver.video.repository;

import com.robot.mediaserver.video.model.MediaEventLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 媒体会话事件日志仓储。
 *
 * @author leelay
 * @date 2026/05/20
 */
public interface MediaEventLogRepository extends JpaRepository<MediaEventLog, String> {

    /**
     * 查询指定会话最近事件。
     *
     * @param sessionId 会话 ID
     * @return 事件日志列表
     */
    List<MediaEventLog> findTop50BySessionIdOrderByCreatedAtDesc(String sessionId);
}
