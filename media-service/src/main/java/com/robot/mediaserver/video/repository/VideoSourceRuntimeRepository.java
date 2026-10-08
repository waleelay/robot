package com.robot.mediaserver.video.repository;

import com.robot.media.common.video.VideoChannel;
import com.robot.media.common.video.VideoQuality;
import com.robot.media.common.video.VideoSourceType;
import com.robot.media.common.video.VideoPublisherMode;
import com.robot.mediaserver.video.model.VideoSourceRuntime;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 媒体源运行态仓储。
 */
public interface VideoSourceRuntimeRepository extends JpaRepository<VideoSourceRuntime, String> {

    /**
     * 在当前事务锁定视频源运行实例，串行处理共享发布状态。
     *
     * @param runtimeId 视频源运行实例 ID，供多个观看会话共享发布状态
     * @return 已锁定运行实例；不存在时为空
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select runtime from VideoSourceRuntime runtime where runtime.runtimeId = :runtimeId")
    Optional<VideoSourceRuntime> findByIdForUpdate(@Param("runtimeId") String runtimeId);

    /**
     * 按 LiveKit 房间名查找视频源运行实例。
     *
     * @param roomName LiveKit 房间名
     * @return 匹配运行实例；不存在时为空
     */
    Optional<VideoSourceRuntime> findByRoomName(String roomName);

    /**
     * 按完整视频源及码流键查找共享发布实例。
     * @param sourceType 视频来源类型
     * @param sourceId 用于精确匹配持久化记录的视频源 ID
     * @param deviceId 设备 ID
     * @param channel 视频通道
     * @param quality 视频清晰度
     * @return 匹配运行实例；不存在时为空
     */
    Optional<VideoSourceRuntime> findBySourceTypeAndSourceIdAndDeviceIdAndChannelAndQuality(
            VideoSourceType sourceType,
            String sourceId,
            String deviceId,
            VideoChannel channel,
            VideoQuality quality);

    /**
     * 查找使用指定发布模式的视频源实例。
     *
     * @param publisherMode 当前发布模式
     * @return 匹配的运行实例列表
     */
    java.util.List<VideoSourceRuntime> findByPublisherMode(VideoPublisherMode publisherMode);

    /**
     * 按视频源查询运行实例，使用稳定 ID 顺序避免批量操作锁序漂移。
     * @param sourceType 视频来源类型
     * @param sourceId 用于精确匹配持久化记录的视频源 ID
     * @return 按运行实例 ID 排序的列表
     */
    java.util.List<VideoSourceRuntime> findBySourceTypeAndSourceIdOrderByRuntimeIdAsc(
            VideoSourceType sourceType,
            String sourceId);

    /**
     * 依据唯一视频源键幂等创建运行实例，冲突时保留已有记录。
     * @param runtimeId 视频源运行实例 ID，供多个观看会话共享发布状态
     * @param sourceType 视频来源类型
     * @param sourceId 用于精确匹配持久化记录的视频源 ID
     * @param deviceId 设备 ID
     * @param channel 视频通道
     * @param quality 视频清晰度
     * @param roomName LiveKit 房间名
     * @param publisherMode 当前发布模式
     * @param publisherRevision 发布模式版本，用于拒绝陈旧发布者操作
     * @param now 本次处理使用的统一服务端时间
     * @return 实际插入行数
     */
    @Modifying
    @Query(value = """
            insert into media_source_runtime (
                runtime_id, source_type, source_id, device_id, channel, quality,
                room_name, publisher_mode, publisher_revision, ingress_operation_revision,
                version, created_at, updated_at
            ) values (
                :runtimeId, :sourceType, :sourceId, :deviceId, :channel, :quality,
                :roomName, :publisherMode, :publisherRevision, 0, 0, :now, :now
            ) on duplicate key update runtime_id = runtime_id
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("runtimeId") String runtimeId,
            @Param("sourceType") String sourceType,
            @Param("sourceId") String sourceId,
            @Param("deviceId") String deviceId,
            @Param("channel") String channel,
            @Param("quality") String quality,
            @Param("roomName") String roomName,
            @Param("publisherMode") String publisherMode,
            @Param("publisherRevision") long publisherRevision,
            @Param("now") OffsetDateTime now);

    /**
     * 在当前事务按完整来源与码流键锁定一个运行实例。
     * @param sourceType 视频来源类型
     * @param sourceId 用于精确匹配持久化记录的视频源 ID
     * @param deviceId 设备 ID
     * @param channel 视频通道
     * @param quality 视频清晰度
     * @return 已锁定的运行实例；不存在时为空
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select runtime from VideoSourceRuntime runtime
             where runtime.sourceType = :sourceType
               and runtime.sourceId = :sourceId
               and runtime.deviceId = :deviceId
               and runtime.channel = :channel
               and runtime.quality = :quality
            """)
    Optional<VideoSourceRuntime> findBySourceForUpdate(
            @Param("sourceType") VideoSourceType sourceType,
            @Param("sourceId") String sourceId,
            @Param("deviceId") String deviceId,
            @Param("channel") VideoChannel channel,
            @Param("quality") VideoQuality quality);
}
