package com.robot.mediaserver.video.repository;

import com.robot.media.common.video.VideoChannel;
import com.robot.media.common.video.VideoQuality;
import com.robot.media.common.video.VideoSourceType;
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

    @Modifying
    @Query(value = """
            insert into media_source_runtime (
                runtime_id, source_type, source_id, device_id, channel, quality,
                room_name, version, created_at, updated_at
            ) values (
                :runtimeId, :sourceType, :sourceId, :deviceId, :channel, :quality,
                :roomName, 0, :now, :now
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
            @Param("now") OffsetDateTime now);

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
