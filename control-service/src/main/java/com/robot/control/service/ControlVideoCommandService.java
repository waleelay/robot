package com.robot.control.service;

import com.robot.control.auth.CurrentUser;
import com.robot.control.call.IntercomBusyException;
import com.robot.control.client.ControlManagementClient;
import com.robot.control.client.ControlMediaServiceClient;
import com.robot.control.dto.ControlStartVideoRequest;
import com.robot.control.messaging.RobotMediaCommandService;
import com.robot.control.dto.CreateVideoSessionRequest;
import com.robot.control.dto.IntercomResponse;
import com.robot.control.dto.SwitchChannelRequest;
import com.robot.control.dto.VideoSessionResponse;
import com.robot.control.dto.VideoStartCommand;
import com.robot.control.dto.IntercomStartCommand;
import com.robot.control.dto.IntercomStatus;
import com.robot.control.dto.VideoChannel;
import com.robot.control.dto.VideoQuality;
import com.robot.control.dto.VideoSessionStatus;
import com.robot.control.dto.VideoSourceType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
/**
 * 控制侧视频操作编排服务。
 *
 * @author leelay
 * @date 2026-07-05
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
     * 管理端客户端，用于校验固定摄像头档案。
     */
    private final ControlManagementClient managementClient;

    /**
     * 构造控制端实时视频命令编排服务。
     *
     * @param mediaServiceClient 媒体服务客户端
     * @param commandService 机器人媒体命令服务
     */
    public ControlVideoCommandService(
            ControlMediaServiceClient mediaServiceClient,
            RobotMediaCommandService commandService,
            ControlManagementClient managementClient) {
        this.mediaServiceClient = mediaServiceClient;
        this.commandService = commandService;
        this.managementClient = managementClient;
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
        mediaRequest.setSourceType(VideoSourceType.ROBOT_CAMERA);
        mediaRequest.setSourceId(robotId);
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
     * 控制侧开始固定摄像头实时视频。
     *
     * @param cameraId 固定摄像头 ID
     * @param request 开始观看请求，可为空
     * @param user 当前操作用户
     * @return 实时视频会话响应
     */
    public VideoSessionResponse startFixedCameraVideo(String cameraId, ControlStartVideoRequest request, CurrentUser user) {
        Map<String, Object> camera = requirePlayableFixedCamera(cameraId);
        ControlStartVideoRequest startRequest = request == null ? new ControlStartVideoRequest() : request;
        CreateVideoSessionRequest mediaRequest = new CreateVideoSessionRequest();
        mediaRequest.setRobotId(cameraId);
        mediaRequest.setSourceType(VideoSourceType.FIXED_CAMERA);
        mediaRequest.setSourceId(cameraId);
        mediaRequest.setDeviceId(cameraId);
        mediaRequest.setChannel(VideoChannel.visible);
        mediaRequest.setQuality(fixedCameraQuality(startRequest, camera));
        mediaRequest.setReuse(startRequest.isReuse());
        mediaRequest.setClientRequestId(startRequest.getClientRequestId());
        VideoSessionResponse response = mediaServiceClient.createVideoSession(mediaRequest, user);
        if (response.status() == VideoSessionStatus.INIT) {
            VideoStartCommand command = mediaServiceClient.requestClientStart(response.sessionId(), "video.fixed_camera.requested");
            command = withFixedCameraRtsp(command, camera, mediaRequest.getQuality());
            sendStart(command);
            return mediaServiceClient.get(response.sessionId(), user);
        }
        return response;
    }

    /**
     * 批量开始固定摄像头实时视频。
     *
     * @param cameraIds 固定摄像头 ID 列表
     * @param request 开始观看请求，可为空
     * @param user 当前操作用户
     * @return 批量启动结果
     */
    public Map<String, Object> startFixedCameraVideos(List<String> cameraIds, ControlStartVideoRequest request, CurrentUser user) {
        if (cameraIds == null || cameraIds.isEmpty()) {
            return Map.of("sessions", List.of(), "failures", List.of());
        }
        List<Map<String, Object>> sessions = new ArrayList<>();
        List<Map<String, Object>> failures = new ArrayList<>();
        cameraIds.stream()
                .filter(cameraId -> cameraId != null && !cameraId.isBlank())
                .distinct()
                .forEach(cameraId -> {
                    try {
                        VideoSessionResponse session = startFixedCameraVideo(cameraId, request, user);
                        sessions.add(Map.<String, Object>of("cameraId", cameraId, "session", session));
                    } catch (RuntimeException exception) {
                        failures.add(Map.of(
                                "cameraId", cameraId,
                                "message", exception.getMessage() == null ? "固定摄像头启动失败" : exception.getMessage()));
                    }
                });
        return Map.of("sessions", sessions, "failures", failures);
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
    public synchronized IntercomResponse startIntercom(
            String robotId,
            String deviceId,
            ControlStartVideoRequest request,
            CurrentUser user) {
        requireIntercomAvailable(robotId, deviceId, null, user);
        ControlStartVideoRequest startRequest = request == null ? new ControlStartVideoRequest() : request;
        CreateVideoSessionRequest mediaRequest = new CreateVideoSessionRequest();
        mediaRequest.setRobotId(robotId);
        mediaRequest.setSourceType(VideoSourceType.ROBOT_CAMERA);
        mediaRequest.setSourceId(robotId);
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
    public synchronized IntercomResponse startIntercom(String sessionId, CurrentUser user) {
        VideoSessionResponse target = mediaServiceClient.get(sessionId, user);
        requireIntercomAvailable(target.robotId(), target.deviceId(), sessionId, user);
        IntercomResponse response = mediaServiceClient.startIntercom(sessionId, user);
        sendIntercomStart(mediaServiceClient.intercomStartCommand(sessionId));
        return response;
    }

    private void requireIntercomAvailable(
            String robotId,
            String deviceId,
            String targetSessionId,
            CurrentUser user) {
        mediaServiceClient.active().stream()
                .filter(session -> occupied(session.intercomStatus()))
                .filter(session -> !Objects.equals(session.sessionId(), targetSessionId))
                .forEach(session -> {
                    boolean sameTarget = Objects.equals(session.robotId(), robotId)
                            && Objects.equals(session.deviceId(), deviceId);
                    boolean sameOwner = Objects.equals(session.intercomOperatorId(), user.userId())
                            && Objects.equals(session.intercomClientId(), user.clientId());
                    if (sameTarget && sameOwner) {
                        return;
                    }
                    if (Objects.equals(session.robotId(), robotId)) {
                        throw new IntercomBusyException("ROBOT_BUSY", "该机器人正在进行其他对讲");
                    }
                    if (Objects.equals(session.intercomOperatorId(), user.userId())) {
                        throw new IntercomBusyException(
                                "OPERATOR_BUSY",
                                "当前操作员正在与其他机器人通话，请先结束当前通话");
                    }
                    if (Objects.equals(session.intercomClientId(), user.clientId())) {
                        throw new IntercomBusyException(
                                "CLIENT_BUSY",
                                "当前终端正在与其他机器人通话，请先结束当前通话");
                    }
                });
    }

    private boolean occupied(IntercomStatus status) {
        return status == IntercomStatus.STARTING || status == IntercomStatus.ACTIVE;
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
        if (command.sourceType() == VideoSourceType.FIXED_CAMERA) {
            commandService.sendFixedCameraRestart(command);
        } else {
            commandService.sendSwitchChannel(command.robotId(), command);
        }
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
        Object sourceType = payload.get("sourceType");
        if (VideoSourceType.FIXED_CAMERA.name().equals(String.valueOf(sourceType))) {
            commandService.sendFixedCameraStop(payload);
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

    /**
     * 下发视频启动命令。
     *
     * @param command 命令内容
     */
    private void sendStart(VideoStartCommand command) {
        if (command != null) {
            if (command.sourceType() == VideoSourceType.FIXED_CAMERA) {
                commandService.sendFixedCameraStart(command);
            } else {
                commandService.sendStart(command);
            }
        }
    }

    private Map<String, Object> requirePlayableFixedCamera(String cameraId) {
        Map<String, Object> camera = managementClient.fixedCamera(cameraId)
                .orElseThrow(() -> new IllegalArgumentException("未找到固定摄像头：" + cameraId));
        if (!Boolean.TRUE.equals(camera.get("enabled"))) {
            throw new IllegalStateException("固定摄像头未启用：" + cameraId);
        }
        Object main = camera.get("mainStreamUrl");
        Object sub = camera.get("subStreamUrl");
        if ((main == null || String.valueOf(main).isBlank()) && (sub == null || String.valueOf(sub).isBlank())) {
            throw new IllegalStateException("固定摄像头未配置码流：" + cameraId);
        }
        return new LinkedHashMap<>(camera);
    }

    private VideoQuality fixedCameraQuality(ControlStartVideoRequest request, Map<String, Object> camera) {
        VideoQuality requested = request == null ? null : request.getQuality();
        boolean hasMain = hasText(camera.get("mainStreamUrl"));
        boolean hasSub = hasText(camera.get("subStreamUrl"));
        if (requested == VideoQuality.sub && !hasSub && hasMain) {
            return VideoQuality.main;
        }
        if (requested == VideoQuality.main && !hasMain && hasSub) {
            return VideoQuality.sub;
        }
        return requested == null ? (hasSub ? VideoQuality.sub : VideoQuality.main) : requested;
    }

    private VideoStartCommand withFixedCameraRtsp(
            VideoStartCommand command,
            Map<String, Object> camera,
            VideoQuality quality) {
        if (command == null) {
            return null;
        }
        return new VideoStartCommand(
                command.commandId(),
                command.sessionId(),
                command.robotId(),
                command.sourceType(),
                command.sourceId(),
                command.deviceId(),
                command.channel(),
                command.quality(),
                command.livekitUrl(),
                command.roomName(),
                command.publisherToken(),
                command.publishIdentity(),
                fixedCameraRtspUrl(camera, quality),
                command.expiresAt());
    }

    private String fixedCameraRtspUrl(Map<String, Object> camera, VideoQuality quality) {
        String main = string(camera.get("mainStreamUrl"));
        String sub = string(camera.get("subStreamUrl"));
        if (quality == VideoQuality.main && hasText(main)) {
            return main;
        }
        if ((quality == VideoQuality.sub || quality == null) && hasText(sub)) {
            return sub;
        }
        return hasText(main) ? main : sub;
    }

    private boolean hasText(Object value) {
        return value != null && !String.valueOf(value).isBlank();
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 发送对讲启动命令。
     *
     * @param command 命令内容
     */
    private void sendIntercomStart(IntercomStartCommand command) {
        if (command != null) {
            commandService.sendIntercomStart(command);
        }
    }
}
