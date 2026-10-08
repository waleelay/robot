package com.robot.mediaserver.video.service;

import com.robot.media.common.video.MediaTrackResponse;
import com.robot.mediaserver.video.dto.VideoSessionResponses;

import com.robot.mediaserver.video.model.MediaTrack;
import com.robot.mediaserver.video.model.VideoSession;
import com.robot.mediaserver.video.repository.MediaTrackRepository;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** 维护轨道发布和取消发布记录，并关联实际 LiveKit 身份。 */
@Service
public class MediaTrackService {

    private final MediaTrackRepository repository;

    /**
     * 初始化 MediaTrackService，保存所需依赖及初始运行状态。
     * @param repository 媒体轨道发布记录仓储
     */
    public MediaTrackService(MediaTrackRepository repository) {
        this.repository = repository;
    }

    /**
     * 按会话与 媒体轨道标识 登记发布事实，同一有效轨道重复事件不创建第二条记录。
     * @param session 业务视频会话实体
     * @param participantIdentity LiveKit 参与者身份
     * @param trackSid LiveKit 轨道标识
     * @param trackName 媒体轨道名称
     */
    @Transactional
    public void publish(
            VideoSession session,
            String participantIdentity,
            String trackSid,
            String trackName) {
        if (trackSid == null || trackSid.isBlank()) {
            return;
        }
        repository.findFirstBySessionIdAndTrackSidAndUnpublishedAtIsNull(session.getSessionId(), trackSid)
                .ifPresentOrElse(track -> {
                    if (!Objects.equals(track.getParticipantIdentity(), participantIdentity)
                            || !Objects.equals(track.getTrackName(), trackName)) {
                        track.setParticipantIdentity(participantIdentity);
                        track.setTrackName(trackName);
                        repository.save(track);
                    }
                }, () -> {
                    MediaTrack track = new MediaTrack();
                    track.setTrackId("track_" + compactUuid());
                    track.setSessionId(session.getSessionId());
                    track.setTrackSid(trackSid);
                    track.setTrackName(trackName);
                    track.setParticipantIdentity(participantIdentity);
                    track.setKind("video");
                    track.setChannel(session.getChannel());
                    track.setQuality(session.getQuality());
                    track.setPublishedAt(now());
                    repository.save(track);
                });
    }

    /**
     * 按会话和 媒体轨道标识 收口尚未取消发布的轨道记录。
     * @param session 业务视频会话实体
     */
    @Transactional
    public void unpublish(VideoSession session) {
        if (session.getTrackSid() == null || session.getTrackSid().isBlank()) {
            return;
        }
        repository.findFirstBySessionIdAndTrackSidAndUnpublishedAtIsNull(session.getSessionId(), session.getTrackSid())
                .ifPresent(track -> {
                    track.setUnpublishedAt(now());
                    repository.save(track);
                });
    }

    /**
     * 查询指定会话最近的轨道发布与取消发布记录。
     *
     * @param sessionId 会话 ID
     * @return 按发布时间倒序的轨道记录
     */
    public List<MediaTrackResponse> recentBySession(String sessionId) {
        return repository.findTop20BySessionIdOrderByPublishedAtDesc(sessionId).stream()
                .map(VideoSessionResponses::from)
                .toList();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private String compactUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
