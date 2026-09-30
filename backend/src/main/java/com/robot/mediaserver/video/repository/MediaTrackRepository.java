package com.robot.mediaserver.video.repository;

import com.robot.mediaserver.video.model.MediaTrack;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 查询会话的活动轨道及最近发布记录。 */
public interface MediaTrackRepository extends JpaRepository<MediaTrack, String> {

    /**
     * 查找指定会话尚未取消发布的同 SID 轨道。
     *
     * @param sessionId 会话 ID
     * @param trackSid LiveKit 轨道标识
     * @return 当前有效轨道记录；不存在时为空
     */
    Optional<MediaTrack> findFirstBySessionIdAndTrackSidAndUnpublishedAtIsNull(String sessionId, String trackSid);

    /**
     * 查询指定会话最近二十条轨道记录。
     *
     * @param sessionId 会话 ID
     * @return 按发布时间倒序排列的轨道列表
     */
    List<MediaTrack> findTop20BySessionIdOrderByPublishedAtDesc(String sessionId);
}
