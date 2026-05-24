package com.robot.mediaserver.control.service;

import com.robot.mediaserver.auth.CurrentUser;
import com.robot.mediaserver.control.dto.ControlStartVideoRequest;
import com.robot.mediaserver.control.messaging.RobotMediaCommandService;
import com.robot.mediaserver.video.dto.CreateVideoSessionRequest;
import com.robot.mediaserver.video.dto.SwitchChannelRequest;
import com.robot.mediaserver.video.dto.VideoSessionResponse;
import com.robot.mediaserver.video.messaging.VideoStartCommand;
import com.robot.mediaserver.video.model.VideoChannel;
import com.robot.mediaserver.video.model.VideoQuality;
import com.robot.mediaserver.video.model.VideoSessionStatus;
import com.robot.mediaserver.video.service.VideoSessionService;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ControlVideoCommandService {

    private final VideoSessionService videoSessionService;
    private final RobotMediaCommandService commandService;

    public ControlVideoCommandService(
            VideoSessionService videoSessionService,
            RobotMediaCommandService commandService) {
        this.videoSessionService = videoSessionService;
        this.commandService = commandService;
    }

    public VideoSessionResponse startVideo(String robotId, String deviceId, ControlStartVideoRequest request, CurrentUser user) {
        ControlStartVideoRequest startRequest = request == null ? new ControlStartVideoRequest() : request;
        CreateVideoSessionRequest mediaRequest = new CreateVideoSessionRequest();
        mediaRequest.setRobotId(robotId);
        mediaRequest.setDeviceId(deviceId);
        mediaRequest.setChannel(startRequest.getChannel() == null ? VideoChannel.visible : startRequest.getChannel());
        mediaRequest.setQuality(startRequest.getQuality() == null ? VideoQuality.sub : startRequest.getQuality());
        mediaRequest.setReuse(startRequest.isReuse());
        mediaRequest.setClientRequestId(startRequest.getClientRequestId());
        VideoSessionResponse response = videoSessionService.create(mediaRequest, user);
        if (response.status() == VideoSessionStatus.INIT) {
            VideoStartCommand command = videoSessionService.requestClientStart(response.sessionId(), "video.client.requested");
            sendStart(command);
            return videoSessionService.get(response.sessionId(), user);
        }
        return response;
    }

    public VideoSessionResponse restartVideo(String sessionId, CurrentUser user) {
        VideoStartCommand command = videoSessionService.restartSessionCommand(sessionId, user);
        sendStart(command);
        return videoSessionService.get(sessionId, user);
    }

    public VideoSessionResponse switchChannel(String sessionId, SwitchChannelRequest request) {
        VideoSessionResponse response = videoSessionService.switchChannel(sessionId, request);
        VideoStartCommand command = videoSessionService.createStartCommand(sessionId);
        commandService.sendSwitchChannel(command.robotId(), command);
        return response;
    }

    public void restartSession(String sessionId) {
        sendStart(videoSessionService.restartSessionCommand(sessionId));
    }

    public void releaseIdleSession(String sessionId) {
        Map<String, Object> payload = videoSessionService.releaseIdleSession(sessionId);
        if (payload.isEmpty()) {
            return;
        }
        Object robotId = payload.get("robotId");
        if (robotId != null) {
            commandService.sendStop(String.valueOf(robotId), payload);
        }
    }

    public void handleClientOnline(String robotId, String status) {
        videoSessionService.handleClientOnline(robotId, status).forEach(this::sendStart);
    }

    private void sendStart(VideoStartCommand command) {
        if (command != null) {
            commandService.sendStart(command);
        }
    }
}
