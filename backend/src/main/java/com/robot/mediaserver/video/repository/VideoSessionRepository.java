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

        /**
         * 锁定业务视频会话行，供状态切换在事务内串行执行。
         *
         * @param sessionId 会话 ID
         * @return 已锁定会话；不存在时为空
         */
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select session from VideoSession session where session.sessionId = :sessionId")
        Optional<VideoSession> findByIdForUpdate(@Param("sessionId") String sessionId);

        /**
         * 查询同一发布运行实例关联的观看会话，并使用稳定 ID 顺序。
         *
         * @param runtimeId 视频源运行实例 ID，供多个观看会话共享发布状态
         * @return 关联观看会话列表
         */
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        List<VideoSession> findByRuntimeIdOrderBySessionIdAsc(String runtimeId);

        /**
         * 按完整视频源键查找尚未绑定运行实例的旧会话，供运行实例迁移及复用。
         * @param sourceType 视频来源类型
         * @param sourceId 用于精确匹配持久化记录的视频源 ID
         * @param deviceId 设备 ID
         * @param channel 视频通道
         * @param quality 视频清晰度
         * @return 按会话 ID 排序的旧记录
         */
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        List<VideoSession> findByRuntimeIdIsNullAndSourceTypeAndSourceIdAndDeviceIdAndChannelAndQualityOrderBySessionIdAsc(
            VideoSourceType sourceType,
            String sourceId,
            String deviceId,
            VideoChannel channel,
            VideoQuality quality);

        /**
         * 按机器人视频键及允许状态查找最新可复用会话。
         *
         * @param robotId 机器人 ID
         * @param deviceId 设备 ID
         * @param channel 视频通道
         * @param quality 视频清晰度
         * @param statuses 允许匹配的会话状态集合
         * @return 最新匹配会话；不存在时为空
         */
        Optional<VideoSession> findFirstByRobotIdAndDeviceIdAndChannelAndQualityAndStatusInOrderByCreatedAtDesc(
            String robotId,
            String deviceId,
            VideoChannel channel,
            VideoQuality quality,
            Collection<VideoSessionStatus> statuses);

        /**
         * 按来源类型、来源 ID 和码流键查找最新匹配会话。
         * @param sourceType 视频来源类型
         * @param sourceId 用于精确匹配持久化记录的视频源 ID
         * @param deviceId 设备 ID
         * @param channel 视频通道
         * @param quality 视频清晰度
         * @param statuses 允许匹配的会话状态集合
         * @return 最新匹配会话；不存在时为空
         */
        Optional<VideoSession> findFirstBySourceTypeAndSourceIdAndDeviceIdAndChannelAndQualityAndStatusInOrderByCreatedAtDesc(
            VideoSourceType sourceType,
            String sourceId,
            String deviceId,
            VideoChannel channel,
            VideoQuality quality,
            Collection<VideoSessionStatus> statuses);

        /**
         * 查找指定运行实例在允许状态下的最新会话。
         *
         * @param runtimeId 视频源运行实例 ID，供多个观看会话共享发布状态
         * @param statuses 允许匹配的会话状态集合
         * @return 最新匹配会话；不存在时为空
         */
        Optional<VideoSession> findFirstByRuntimeIdAndStatusInOrderByCreatedAtDesc(
            String runtimeId,
            Collection<VideoSessionStatus> statuses);

        /**
         * 查找指定状态且发布请求已超过阈值的会话。
         *
         * @param status 当前业务状态，取值遵循所属模型的状态协议
         * @param commandRequestedAt 最近请求发布媒体指令的时间
         * @return 候选发布超时会话
         */
        List<VideoSession> findByStatusAndCommandRequestedAtBefore(
            VideoSessionStatus status,
            OffsetDateTime commandRequestedAt);

        /**
         * 按来源类型查找发布请求超过阈值的指定状态会话。
         *
         * @param status 当前业务状态，取值遵循所属模型的状态协议
         * @param sourceType 视频来源类型
         * @param commandRequestedAt 最近请求发布媒体指令的时间
         * @return 对应来源的候选超时会话
         */
        List<VideoSession> findByStatusAndSourceTypeAndCommandRequestedAtBefore(
            VideoSessionStatus status,
            VideoSourceType sourceType,
            OffsetDateTime commandRequestedAt);

        /**
         * 按最近状态时间查找可能失联的指定状态会话。
         *
         * @param status 当前业务状态，取值遵循所属模型的状态协议
         * @param lastStatusAt 最近接受视频状态上报的时间
         * @return 候选陈旧状态会话
         */
        List<VideoSession> findByStatusAndLastStatusAtBefore(VideoSessionStatus status, OffsetDateTime lastStatusAt);

        /**
         * 从指定会话状态去重取得需要核验的发布运行实例。
         *
         * @param statuses 允许匹配的会话状态集合
         * @return 去重后的运行实例 ID
         */
        @Query("select distinct session.runtimeId from VideoSession session where session.runtimeId is not null and session.status in :statuses")
        List<String> findDistinctRuntimeIdsByStatusIn(@Param("statuses") Collection<VideoSessionStatus> statuses);

        /**
         * 查询指定状态下空闲时间超过阈值的会话。
         *
         * @param status 当前业务状态，取值遵循所属模型的状态协议
         * @param idleSince 会话开始无人观看的时间，用于延迟回收
         * @return 候选空闲回收会话
         */
        List<VideoSession> findByStatusAndIdleSinceBefore(VideoSessionStatus status, OffsetDateTime idleSince);

        /**
         * 按查询条件选择无有效占用的会话，供后台有界回收。
         *
         * @param statuses 允许匹配的会话状态集合
         * @param pageable 查询分页与排序条件
         * @return 待检查的会话 ID
         */
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

    /**
     * 按对讲期限与心跳阈值选择候选超时会话。
     *
     * @param statuses 允许匹配的会话状态集合
     * @param heartbeatBefore 心跳时间阈值
     * @return 待收口的对讲会话
     */
    @Query("""
            select session from VideoSession session
            where session.intercomStatus in :statuses
              and (session.intercomHeartbeatAt is null or session.intercomHeartbeatAt < :heartbeatBefore)
            """)
    List<VideoSession> findIntercomTimeoutCandidates(
            @Param("statuses") Collection<IntercomStatus> statuses,
            @Param("heartbeatBefore") OffsetDateTime heartbeatBefore);

    /**
     * 查询处于指定对讲状态集合的会话。
     *
     * @param statuses 允许匹配的会话状态集合
     * @return 匹配的对讲会话列表
     */
    List<VideoSession> findByIntercomStatusIn(Collection<IntercomStatus> statuses);

    /**
     * 查询允许状态中的最近十六个会话。
     *
     * @param statuses 允许匹配的会话状态集合
     * @return 按更新时间倒序排列的会话
     */
    List<VideoSession> findTop16ByStatusInOrderByUpdatedAtDesc(Collection<VideoSessionStatus> statuses);

    /**
     * 查询指定机器人仍有观看者的有效会话，供上线恢复。
     *
     * @param robotId 机器人 ID
     * @param viewerCount 观看人数
     * @param statuses 允许匹配的会话状态集合
     * @return 待恢复或复用的会话列表
     */
    List<VideoSession> findByRobotIdAndViewerCountGreaterThanAndStatusInOrderByUpdatedAtDesc(
            String robotId,
            int viewerCount,
            Collection<VideoSessionStatus> statuses);

    /**
     * 按视频源类型查询指定状态会话。
     *
     * @param sourceType 视频来源类型
     * @param statuses 允许匹配的会话状态集合
     * @return 按更新时间倒序排列的会话
     */
    List<VideoSession> findBySourceTypeAndStatusInOrderByUpdatedAtDesc(
            VideoSourceType sourceType,
            Collection<VideoSessionStatus> statuses);

        /**
         * 查询指定用户最近创建的二十个会话。
         *
         * @param createdBy 创建该记录的用户标识
         * @return 按创建时间倒序排列的会话
         */
        List<VideoSession> findTop20ByCreatedByOrderByCreatedAtDesc(String createdBy);
}
