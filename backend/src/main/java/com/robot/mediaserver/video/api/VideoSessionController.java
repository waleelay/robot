package com.robot.mediaserver.video.api;

import com.robot.mediaserver.auth.CurrentUser;
import com.robot.mediaserver.auth.CurrentUserResolver;
import com.robot.mediaserver.video.dto.CreateSnapshotRequest;
import com.robot.mediaserver.video.dto.CreateVideoSessionRequest;
import com.robot.mediaserver.video.dto.SnapshotResponse;
import com.robot.mediaserver.video.dto.SwitchChannelRequest;
import com.robot.mediaserver.video.dto.VideoSessionResponse;
import com.robot.mediaserver.video.dto.ViewerTokenResponse;
import com.robot.mediaserver.video.service.VideoSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 实时视频会话 API。
 *
 * <p>该控制器只暴露媒体服务的业务接口；真实视频播放由前端使用返回的 LiveKit
 * URL 和 token 直接加入 Room 完成。</p>
 *
 * @author leelay
 * @date 2026/05/19
 */
@RestController
@RequestMapping("/api/media/video-sessions")
public class VideoSessionController {

    private final VideoSessionService service;
    private final CurrentUserResolver currentUserResolver;

    public VideoSessionController(VideoSessionService service, CurrentUserResolver currentUserResolver) {
        this.service = service;
        this.currentUserResolver = currentUserResolver;
    }

    /**
     * 创建实时视频会话。
     *
     * <p>后端会创建或复用会话，并向云接入客户端下发启动发布指令。</p>
     */
    @PostMapping
    public VideoSessionResponse create(@Valid @RequestBody CreateVideoSessionRequest request, HttpServletRequest servletRequest) {
        return service.create(request, currentUserResolver.resolve(servletRequest));
    }

    @GetMapping
    public List<VideoSessionResponse> recent(HttpServletRequest servletRequest) {
        return service.recent(currentUserResolver.resolve(servletRequest));
    }

    @GetMapping("/{sessionId}")
    public VideoSessionResponse get(@PathVariable String sessionId, HttpServletRequest servletRequest) {
        return service.get(sessionId, currentUserResolver.resolve(servletRequest));
    }

    /**
     * 为当前用户签发观看 Token。
     */
    @PostMapping("/{sessionId}/token")
    public ViewerTokenResponse token(@PathVariable String sessionId, HttpServletRequest servletRequest) {
        CurrentUser user = currentUserResolver.resolve(servletRequest);
        return service.createViewerToken(sessionId, user);
    }

    /**
     * 停止当前用户观看。
     *
     * <p>多人观看时不会立即释放机器人端推流，最后一个观看者退出后才释放。</p>
     */
    @PostMapping("/{sessionId}/stop")
    public VideoSessionResponse stop(@PathVariable String sessionId, HttpServletRequest servletRequest) {
        return service.stop(sessionId, currentUserResolver.resolve(servletRequest));
    }

    /**
     * 切换媒体通道，例如可见光和热成像。
     */
    @PostMapping("/{sessionId}/switch-channel")
    public VideoSessionResponse switchChannel(@PathVariable String sessionId, @Valid @RequestBody SwitchChannelRequest request) {
        return service.switchChannel(sessionId, request);
    }

    /**
     * 提交抓拍任务。
     *
     * <p>前端可先做即时预览，服务端后续通过 Snapshot Worker 生成正式图片。</p>
     */
    @PostMapping("/{sessionId}/snapshots")
    public SnapshotResponse snapshot(
            @PathVariable String sessionId,
            @Valid @RequestBody CreateSnapshotRequest request,
            HttpServletRequest servletRequest) {
        return service.createSnapshot(sessionId, request, currentUserResolver.resolve(servletRequest));
    }

    /**
     * 开发联调接口：模拟云接入客户端 ACK。
     */
    @PostMapping("/{sessionId}/_mock/client-acked")
    public VideoSessionResponse mockClientAcked(@PathVariable String sessionId) {
        return service.markClientAcked(sessionId);
    }

    /**
     * 开发联调接口：模拟目标 Track 已发布。
     */
    @PostMapping("/{sessionId}/_mock/track-published/{trackSid}")
    public VideoSessionResponse mockTrackPublished(@PathVariable String sessionId, @PathVariable String trackSid) {
        return service.markTrackPublished(sessionId, trackSid);
    }
}
