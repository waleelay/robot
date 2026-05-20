package com.robot.mediaserver.livekit.webhook;

import com.robot.mediaserver.video.service.VideoSessionService;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * LiveKit webhook 回调接口。
 *
 * <p>用于接收 LiveKit 的 Track、Participant、Room 事件，并回写实时视频会话状态。
 * 当前阶段先按事件体解析 room/track/participant，后续可补充 LiveKit webhook 签名校验。</p>
 *
 * @author leelay
 * @date 2026/05/20
 */
@RestController
@RequestMapping("/api/internal/livekit")
public class LiveKitWebhookController {

    private final VideoSessionService videoSessionService;

    public LiveKitWebhookController(VideoSessionService videoSessionService) {
        this.videoSessionService = videoSessionService;
    }

    /**
     * 接收 LiveKit webhook 事件。
     *
     * @param payload webhook 原始 JSON
     */
    @PostMapping("/webhook")
    public void webhook(@RequestBody Map<String, Object> payload) {
        String event = stringValue(payload.get("event"));
        String roomName = roomName(payload);
        if (roomName == null || roomName.isBlank()) {
            return;
        }
        switch (event) {
            case "track_published" -> videoSessionService.handleLiveKitTrackPublished(
                    roomName,
                    trackValue(payload, "sid"),
                    trackValue(payload, "name"));
            case "track_unpublished" -> videoSessionService.handleLiveKitTrackInterrupted(roomName, "track unpublished");
            case "participant_left" -> {
                if (isRobotParticipant(payload)) {
                    videoSessionService.handleLiveKitTrackInterrupted(roomName, "robot participant left");
                }
            }
            case "room_finished" -> videoSessionService.handleLiveKitRoomFinished(roomName);
            default -> {
                // 其他事件暂不影响实时视频状态机。
            }
        }
    }

    private String roomName(Map<String, Object> payload) {
        Object room = payload.get("room");
        if (room instanceof Map<?, ?> roomMap) {
            return stringValue(roomMap.get("name"));
        }
        return stringValue(payload.get("roomName"));
    }

    private String trackValue(Map<String, Object> payload, String key) {
        Object track = payload.get("track");
        if (track instanceof Map<?, ?> trackMap) {
            return stringValue(trackMap.get(key));
        }
        return null;
    }

    private boolean isRobotParticipant(Map<String, Object> payload) {
        Object participant = payload.get("participant");
        if (participant instanceof Map<?, ?> participantMap) {
            String identity = stringValue(participantMap.get("identity"));
            return identity != null && identity.startsWith("robot:");
        }
        return false;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
