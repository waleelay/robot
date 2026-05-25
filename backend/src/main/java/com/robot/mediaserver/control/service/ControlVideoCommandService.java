package com.robot.mediaserver.control.service;

import com.robot.mediaserver.auth.CurrentUser;
import com.robot.mediaserver.control.client.ControlMediaServiceClient;
import com.robot.mediaserver.control.dto.ControlStartVideoRequest;
import com.robot.mediaserver.control.messaging.RobotMediaCommandService;
import com.robot.mediaserver.video.dto.CreateVideoSessionRequest;
import com.robot.mediaserver.video.dto.IntercomResponse;
import com.robot.mediaserver.video.dto.SwitchChannelRequest;
import com.robot.mediaserver.video.dto.VideoSessionResponse;
import com.robot.mediaserver.video.messaging.VideoStartCommand;
import com.robot.mediaserver.video.messaging.IntercomStartCommand;
import com.robot.mediaserver.video.model.VideoChannel;
import com.robot.mediaserver.video.model.VideoQuality;
import com.robot.mediaserver.video.model.VideoSessionStatus;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ControlVideoCommandService {

    private final ControlMediaServiceClient mediaServiceClient;
    private final RobotMediaCommandService commandService;

    public ControlVideoCommandService(
            ControlMediaServiceClient mediaServiceClient,
            RobotMediaCommandService commandService) {
        this.mediaServiceClient = mediaServiceClient;
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
        VideoSessionResponse response = mediaServiceClient.createVideoSession(mediaRequest, user);
        if (response.status() == VideoSessionStatus.INIT) {
            VideoStartCommand command = mediaServiceClient.requestClientStart(response.sessionId(), "video.client.requested");
            sendStart(command);
            return mediaServiceClient.get(response.sessionId(), user);
        }
        if (response.intercomAudioOnly() && response.trackSid() == null) {
            VideoStartCommand command = mediaServiceClient.requestClientStart(response.sessionId(), "video.client.requested");
            sendStart(command);
            return mediaServiceClient.get(response.sessionId(), user);
        }
        return response;
    }

    public IntercomResponse startIntercom(
            String robotId,
            String deviceId,
            ControlStartVideoRequest request,
            CurrentUser user) {
        ControlStartVideoRequest startRequest = request == null ? new ControlStartVideoRequest() : request;
        CreateVideoSessionRequest mediaRequest = new CreateVideoSessionRequest();
        mediaRequest.setRobotId(robotId);
        mediaRequest.setDeviceId(deviceId);
        mediaRequest.setChannel(startRequest.getChannel() == null ? VideoChannel.visible : startRequest.getChannel());
        mediaRequest.setQuality(startRequest.getQuality() == null ? VideoQuality.sub : startRequest.getQuality());
        mediaRequest.setReuse(true);
        IntercomResponse response = mediaServiceClient.createIntercom(mediaRequest, user);
        sendIntercomStart(mediaServiceClient.intercomStartCommand(response.sessionId()));
        return response;
    }

    public IntercomResponse startIntercom(String sessionId, CurrentUser user) {
        IntercomResponse response = mediaServiceClient.startIntercom(sessionId, user);
        sendIntercomStart(mediaServiceClient.intercomStartCommand(sessionId));
        return response;
    }

    public VideoSessionResponse stopIntercom(String sessionId, CurrentUser user) {
        VideoSessionResponse response = mediaServiceClient.stopIntercom(sessionId, user);
        commandService.sendIntercomStop(response.robotId(), Map.of(
                "sessionId", response.sessionId(),
                "roomName", response.roomName(),
                "commandId", "cmd_intercom_stop_" + response.sessionId()));
        return response;
    }

    public void expireIntercom(String sessionId) {
        Map<String, Object> payload = mediaServiceClient.expireIntercom(sessionId);
        if (!payload.isEmpty() && payload.get("robotId") != null) {
            commandService.sendIntercomStop(String.valueOf(payload.get("robotId")), payload);
        }
    }

    public VideoSessionResponse restartVideo(String sessionId, CurrentUser user) {
        VideoStartCommand command = mediaServiceClient.restartCommand(sessionId, user);
        sendStart(command);
        return mediaServiceClient.get(sessionId, user);
    }

    public VideoSessionResponse switchChannel(String sessionId, SwitchChannelRequest request) {
        VideoSessionResponse response = mediaServiceClient.switchChannel(sessionId, request);
        VideoStartCommand command = mediaServiceClient.currentStartCommand(sessionId);
        commandService.sendSwitchChannel(command.robotId(), command);
        return response;
    }

    public void restartSession(String sessionId) {
        sendStart(mediaServiceClient.restartCommand(sessionId, null));
    }

    public void releaseIdleSession(String sessionId) {
        Map<String, Object> payload = mediaServiceClient.releaseIdle(sessionId);
        if (payload.isEmpty()) {
            return;
        }
        Object robotId = payload.get("robotId");
        if (robotId != null) {
            commandService.sendStop(String.valueOf(robotId), payload);
        }
    }

    public void handleClientOnline(String robotId, String status) {
        mediaServiceClient.onlineRestartCommands(robotId, status).forEach(this::sendStart);
    }

    private void sendStart(VideoStartCommand command) {
        if (command != null) {
            commandService.sendStart(command);
        }
    }

    private void sendIntercomStart(IntercomStartCommand command) {
        if (command != null) {
            commandService.sendIntercomStart(command);
        }
    }
}
