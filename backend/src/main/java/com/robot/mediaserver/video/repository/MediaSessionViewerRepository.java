package com.robot.mediaserver.video.repository;

import com.robot.mediaserver.video.model.MediaSessionViewer;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** 读写会话观看者和活动租约，支撑心跳续期与过期清理。 */
public interface MediaSessionViewerRepository extends JpaRepository<MediaSessionViewer, String> {

    /**
     * 按会话和观看者身份幂等登记有效观看租约。
     * @param viewerId 观看者身份标识
     * @param sessionId 会话 ID
     * @param userId 用户 ID
     * @param orgId 组织 ID
     * @param identity 由用户 ID 和客户端 ID 组成的 LiveKit 观看者身份
     * @param clientId 客户端 ID
     * @param heartbeatAt 本次观看者心跳时间
     * @return 数据库实际影响的行数
     */
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

    /**
     * 统计尚未标记离开的观看租约。
     *
     * @param sessionId 会话 ID
     * @return 该会话的活动观看者数量
     */
    long countBySessionIdAndLeftAtIsNull(String sessionId);

    /**
     * 查询未离开且心跳早于阈值的观看租约。
     *
     * @param lastHeartbeatAt 最后心跳时间
     * @return 候选过期租约
     */
    List<MediaSessionViewer> findByLeftAtIsNullAndLastHeartbeatAtBefore(OffsetDateTime lastHeartbeatAt);

    /**
     * 仅关闭匹配身份的活动租约，重复关闭不产生额外占用变化。
     * @param sessionId 会话 ID
     * @param identity 由用户 ID 和客户端 ID 组成的 LiveKit 观看者身份
     * @param leftAt 本次关闭租约所记录的离开时间
     * @return 实际关闭的租约数量
     */
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

    /**
     * 收口指定会话全部仍有效的观看租约。
     * @param sessionId 会话 ID
     * @param leftAt 本次关闭租约所记录的离开时间
     * @return 实际关闭的租约数量
     */
    @Modifying
    @Query("""
            update MediaSessionViewer viewer
               set viewer.leftAt = :leftAt, viewer.activeLeaseKey = null
             where viewer.sessionId = :sessionId
               and viewer.leftAt is null
            """)
    int closeActiveLeasesBySessionId(
            @Param("sessionId") String sessionId,
            @Param("leftAt") OffsetDateTime leftAt);

    /**
     * 关闭心跳仍早于扫描阈值的租约，避免扫描后新心跳被误关闭。
     * @param viewerId 观看者身份标识
     * @param heartbeatBefore 心跳时间阈值
     * @param leftAt 本次关闭租约所记录的离开时间
     * @return 成功关闭的租约数量
     */
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
