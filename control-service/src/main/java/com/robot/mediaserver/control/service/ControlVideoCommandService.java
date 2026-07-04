package com.robot.mediaserver.control.service;

import com.robot.mediaserver.control.auth.CurrentUser;
import com.robot.mediaserver.control.client.ControlMediaServiceClient;
import com.robot.mediaserver.control.dto.ControlStartVideoRequest;
import com.robot.mediaserver.control.messaging.RobotMediaCommandService;
import com.robot.mediaserver.control.dto.CreateVideoSessionRequest;
import com.robot.mediaserver.control.dto.IntercomResponse;
import com.robot.mediaserver.control.dto.SwitchChannelRequest;
import com.robot.mediaserver.control.dto.VideoSessionResponse;
import com.robot.mediaserver.control.dto.VideoStartCommand;
import com.robot.mediaserver.control.dto.IntercomStartCommand;
import com.robot.mediaserver.control.dto.VideoChannel;
import com.robot.mediaserver.control.dto.VideoQuality;
import com.robot.mediaserver.control.dto.VideoSessionStatus;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 控制端实时视频命令编排服务。
 *
 * <p>负责把控制端 API 请求转换为媒体服务会话请求，并在需要机器人执行动作时，
 * 通过 MQTT 命令服务下发开始推流、停止推流、切换通道和对讲控制命令。</p>
 *
 * @author leelay
 * @date 2026/05/31
 */
@Service
public class ControlVideoCommandService {

    /**
     * 媒体服务客户端，用于创建、查询和更新实时视频会话。
     */
    private final ControlMediaServiceClient mediaServiceClient;

    /**
     * 机器人媒体命令服务，用于向机器人客户端发布 MQTT 控制命令。
     */
    private final RobotMediaCommandService commandService;

    /**
     * 构造控制端实时视频命令编排服务。
     *
     * @param mediaServiceClient 媒体服务客户端
     * @param commandService 机器人媒体命令服务
     */
    public ControlVideoCommandService(
            ControlMediaServiceClient mediaServiceClient,
            RobotMediaCommandService commandService) {
        this.mediaServiceClient = mediaServiceClient;
        this.commandService = commandService;
    }

    /**
     * 控制侧开始实时视频的门面方法。
     *
     * <p>这里把面向前端/控制 API 的请求转换为媒体服务请求。媒体服务只负责创建、
     * 复用和维护 VideoSession；真正让机器人开始推流的 MQTT start 指令由本类发出，
     * 这样控制 API 可以统一管理“会话状态”和“机器人命令”两个边界。</p>
     *
     * @param robotId 机器人编号
     * @param deviceId 摄像头设备编号
     * @param request 开始观看请求，可为空
     * @param user 当前操作用户
     * @return 实时视频会话响应
     */
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
        // INIT 表示新会话刚落库，还没有机器人端推流。此时必须请求媒体服务生成
        // publisher token/roomName，再通过 MQTT 下发给机器人客户端。
        if (response.status() == VideoSessionStatus.INIT) {
            VideoStartCommand command = mediaServiceClient.requestClientStart(response.sessionId(), "video.client.requested");
            sendStart(command);
            return mediaServiceClient.get(response.sessionId(), user);
        }
        // 对讲先创建的 audio-only 会话没有 video track。用户随后点“观看”时，需要补发
        // 视频 start 指令，把同一个 Room 从对讲升级成音视频会话。
        if (response.intercomAudioOnly() && response.trackSid() == null) {
            VideoStartCommand command = mediaServiceClient.requestClientStart(response.sessionId(), "video.client.requested");
            sendStart(command);
            return mediaServiceClient.get(response.sessionId(), user);
        }
        return response;
    }

    /**
     * 从摄像头入口发起对讲。
     *
     * <p>对讲强制 reuse=true：如果画面已经有人在看，就复用同一个 LiveKit Room；
     * 如果没有会话，媒体服务会创建一个临时 VideoSession 作为对讲房间容器。</p>
     *
     * @param robotId 机器人编号
     * @param deviceId 摄像头设备编号
     * @param request 对讲请求，可为空
     * @param user 当前操作用户
     * @return 对讲启动响应
     */
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

    /**
     * 在已有视频会话上开启对讲，并通知机器人启动音频采集/播放桥。
     *
     * @param sessionId 实时视频会话编号
     * @param user 当前操作用户
     * @return 对讲启动响应
     */
    public IntercomResponse startIntercom(String sessionId, CurrentUser user) {
        IntercomResponse response = mediaServiceClient.startIntercom(sessionId, user);
        sendIntercomStart(mediaServiceClient.intercomStartCommand(sessionId));
        return response;
    }

    /**
     * 操作员挂断对讲：先更新媒体服务状态，再给机器人发送 intercom stop。
     *
     * @param sessionId 实时视频会话编号
     * @param user 当前操作用户
     * @return 挂断后的实时视频会话响应
     */
    public VideoSessionResponse stopIntercom(String sessionId, CurrentUser user) {
        VideoSessionResponse response = mediaServiceClient.stopIntercom(sessionId, user);
        commandService.sendIntercomStop(response.robotId(), Map.of(
                "sessionId", response.sessionId(),
                "roomName", response.roomName(),
                "commandId", "cmd_intercom_stop_" + response.sessionId()));
        return response;
    }

    /**
     * 后台调度器发现对讲心跳超时后调用。
     *
     * <p>只有媒体服务确认仍需停止机器人端音频桥时，才会返回包含 robotId 的 payload。</p>
     *
     * @param sessionId 实时视频会话编号
     */
    public void expireIntercom(String sessionId) {
        Map<String, Object> payload = mediaServiceClient.expireIntercom(sessionId);
        if (!payload.isEmpty() && payload.get("robotId") != null) {
            commandService.sendIntercomStop(String.valueOf(payload.get("robotId")), payload);
        }
    }

    /**
     * 手动重启实时视频。媒体服务重新生成 start command，本类只负责下发。
     *
     * @param sessionId 实时视频会话编号
     * @param user 当前操作用户
     * @return 重启后的实时视频会话响应
     */
    public VideoSessionResponse restartVideo(String sessionId, CurrentUser user) {
        VideoStartCommand command = mediaServiceClient.restartCommand(sessionId, user);
        sendStart(command);
        return mediaServiceClient.get(sessionId, user);
    }

    /**
     * 切换通道/码流会更新媒体会话，再通知机器人以新参数重新推流。
     *
     * @param sessionId 实时视频会话编号
     * @param request 通道切换请求
     * @return 切换后的实时视频会话响应
     */
    public VideoSessionResponse switchChannel(String sessionId, SwitchChannelRequest request) {
        VideoSessionResponse response = mediaServiceClient.switchChannel(sessionId, request);
        VideoStartCommand command = mediaServiceClient.currentStartCommand(sessionId);
        commandService.sendSwitchChannel(command.robotId(), command);
        return response;
    }

    /**
     * 调度器或恢复流程使用的无用户重启入口。
     *
     * @param sessionId 实时视频会话编号
     */
    public void restartSession(String sessionId) {
        sendStart(mediaServiceClient.restartCommand(sessionId, null));
    }

    /**
     * 释放空闲会话时，如果媒体服务真正关闭了 Room，这里把 stop 命令下发到机器人。
     *
     * @param sessionId 实时视频会话编号
     */
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

    /**
     * 机器人客户端上线后，后端会把仍有观看者的中断/失败会话重新下发 start。
     *
     * @param robotId 机器人编号
     * @param status 机器人客户端状态
     */
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
