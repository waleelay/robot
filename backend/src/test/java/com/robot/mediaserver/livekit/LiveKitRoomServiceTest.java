package com.robot.mediaserver.livekit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LiveKitRoomServiceTest {

    @Test
    void resolvesActualVideoTrackWhenPreferredSidIsPlaceholder() {
        Map<String, Object> response = Map.of("participants", List.of(
                Map.of("identity", "robot:robot-001:camera01", "sid", "PA_robot", "tracks", List.of(
                        Map.of("sid", "TR_audio", "type", "AUDIO", "source", "MICROPHONE"),
                        Map.of("sid", "TR_actual", "type", "VIDEO", "source", "CAMERA")))));

        assertThat(LiveKitRoomService.resolveVideoTrack(
                response, "robot:robot-001:camera01", "TR_vs_placeholder"))
                .get()
                .extracting(LiveKitRoomService.ActiveVideoTrack::trackSid)
                .isEqualTo("TR_actual");
    }

    @Test
    void prefersMatchingVideoTrackSid() {
        Map<String, Object> response = Map.of("participants", List.of(
                Map.of("identity", "robot:robot-001:camera01", "sid", "PA_robot", "tracks", List.of(
                        Map.of("sid", "TR_first", "type", "VIDEO"),
                        Map.of("sid", "TR_expected", "type", "VIDEO")))));

        assertThat(LiveKitRoomService.resolveVideoTrack(
                response, "robot:robot-001:camera01", "TR_expected"))
                .get()
                .extracting(LiveKitRoomService.ActiveVideoTrack::trackSid)
                .isEqualTo("TR_expected");
    }

    @Test
    void rejectsVideoTrackFromUnexpectedPublisher() {
        Map<String, Object> response = Map.of("participants", List.of(
                Map.of("identity", "robot:other:camera01", "sid", "PA_other", "tracks", List.of(
                        Map.of("sid", "TR_other", "type", "VIDEO")))));

        assertThat(LiveKitRoomService.resolveVideoTrack(
                response, "robot:robot-001:camera01", null)).isEmpty();
    }
}
