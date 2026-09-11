package com.robot.mediaserver.video.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.robot.mediaserver.video.model.MediaTrack;
import com.robot.mediaserver.video.model.VideoSession;
import com.robot.mediaserver.video.repository.MediaTrackRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MediaTrackServiceTest {

    @Test
    void replacesSynthesizedIdentityWithActualLiveKitIdentity() {
        MediaTrackRepository repository = mock(MediaTrackRepository.class);
        MediaTrackService service = new MediaTrackService(repository);
        VideoSession session = new VideoSession();
        session.setSessionId("vs-fixed");
        MediaTrack track = new MediaTrack();
        track.setTrackSid("TR_actual");
        track.setTrackName("video.visible.sub");
        track.setParticipantIdentity("robot:camera-001:camera01");
        when(repository.findFirstBySessionIdAndTrackSidAndUnpublishedAtIsNull("vs-fixed", "TR_actual"))
                .thenReturn(Optional.of(track));

        service.publish(session, "fixed-camera:camera-001", "TR_actual", "video.visible.sub");

        assertThat(track.getParticipantIdentity()).isEqualTo("fixed-camera:camera-001");
        verify(repository).save(track);
    }
}
