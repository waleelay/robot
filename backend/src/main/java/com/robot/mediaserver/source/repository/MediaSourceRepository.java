package com.robot.mediaserver.source.repository;

import com.robot.mediaserver.source.model.MediaSource;
import com.robot.mediaserver.video.model.VideoChannel;
import com.robot.mediaserver.video.model.VideoQuality;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaSourceRepository extends JpaRepository<MediaSource, String> {

    Optional<MediaSource> findFirstByRobotIdAndDeviceIdAndChannelAndQualityAndEnabledTrueOrderByUpdatedAtDesc(
            String robotId,
            String deviceId,
            VideoChannel channel,
            VideoQuality quality);

    List<MediaSource> findTop100ByOrderByUpdatedAtDesc();
}
