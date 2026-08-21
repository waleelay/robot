package com.robot.mediaserver.livekit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LiveKitRoomServiceTest {

    @Test
    void resolvesActualVideoTrackWhenPreferredSidIsPlaceholder() {
        Map<String, Object> response = Map.of("participants", List.of(
                Map.of("tracks", List.of(
                        Map.of("sid", "TR_audio", "type", "AUDIO", "source", "MICROPHONE"),
                        Map.of("sid", "TR_actual", "type", "VIDEO", "source", "CAMERA")))));

        assertThat(LiveKitRoomService.resolveVideoTrackSid(response, "TR_vs_placeholder"))
                .contains("TR_actual");
    }

    @Test
    void prefersMatchingVideoTrackSid() {
        Map<String, Object> response = Map.of("participants", List.of(
                Map.of("tracks", List.of(
                        Map.of("sid", "TR_first", "type", "VIDEO"),
                        Map.of("sid", "TR_expected", "type", "VIDEO")))));

        assertThat(LiveKitRoomService.resolveVideoTrackSid(response, "TR_expected"))
                .contains("TR_expected");
    }
}
