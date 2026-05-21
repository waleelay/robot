package com.robot.mediaserver.source.service;

import com.robot.mediaserver.config.MediaProperties;
import com.robot.mediaserver.source.dto.MediaSourceRequest;
import com.robot.mediaserver.source.dto.MediaSourceResponse;
import com.robot.mediaserver.source.model.MediaSource;
import com.robot.mediaserver.source.repository.MediaSourceRepository;
import com.robot.mediaserver.video.model.VideoChannel;
import com.robot.mediaserver.video.model.VideoQuality;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class MediaSourceService {

    private final MediaSourceRepository repository;
    private final MediaProperties properties;

    public MediaSourceService(MediaSourceRepository repository, MediaProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    public List<MediaSourceResponse> list() {
        return repository.findTop100ByOrderByUpdatedAtDesc().stream()
                .map(MediaSourceResponse::from)
                .toList();
    }

    @Transactional
    public MediaSourceResponse save(MediaSourceRequest request) {
        MediaSource source = new MediaSource();
        source.setSourceId("src_" + compactUuid());
        source.setCreatedAt(now());
        copy(source, request);
        repository.save(source);
        return MediaSourceResponse.from(source);
    }

    @Transactional
    public MediaSourceResponse update(String sourceId, MediaSourceRequest request) {
        MediaSource source = repository.findById(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("Media source not found: " + sourceId));
        copy(source, request);
        repository.save(source);
        return MediaSourceResponse.from(source);
    }

    @Transactional
    public void delete(String sourceId) {
        repository.deleteById(sourceId);
    }

    public String rtspUrl(String robotId, String deviceId, VideoChannel channel, VideoQuality quality) {
        VideoQuality resolvedQuality = quality == VideoQuality.auto ? VideoQuality.sub : quality;
        return repository.findFirstByRobotIdAndDeviceIdAndChannelAndQualityAndEnabledTrueOrderByUpdatedAtDesc(
                        robotId,
                        deviceId,
                        channel,
                        resolvedQuality)
                .map(MediaSource::getRtspUrl)
                .orElse(properties.getRtsp().getDefaultUrl());
    }

    private void copy(MediaSource source, MediaSourceRequest request) {
        source.setRobotId(request.getRobotId());
        source.setDeviceId(request.getDeviceId());
        source.setChannel(request.getChannel());
        source.setQuality(request.getQuality());
        source.setRtspUrl(request.getRtspUrl());
        source.setEnabled(request.isEnabled());
        source.setName(request.getName());
        source.setUpdatedAt(now());
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private String compactUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
