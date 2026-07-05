package com.robot.control.client;

import com.robot.control.auth.CurrentUser;
import com.robot.control.config.ControlProperties;
import com.robot.control.dto.FileDownloadUrlResponse;
import com.robot.control.dto.FileListItemResponse;
import com.robot.control.dto.FileListResponse;
import com.robot.control.dto.FilePlayUrlResponse;
import com.robot.control.dto.FileStatus;
import com.robot.control.dto.FileType;
import com.robot.control.dto.CreateVideoSessionRequest;
import com.robot.control.dto.MediaEventLogResponse;
import com.robot.control.dto.MediaTrackResponse;
import com.robot.control.dto.IntercomResponse;
import com.robot.control.dto.SwitchChannelRequest;
import com.robot.control.dto.VideoSessionResponse;
import com.robot.control.dto.ViewerTokenResponse;
import com.robot.control.dto.VideoStartCommand;
import com.robot.control.dto.VideoStatusMessage;
import com.robot.control.dto.IntercomStartCommand;
import com.robot.control.dto.IntercomStatusMessage;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Control Service 调用 Media Service 内部接口的 HTTP 客户端。
 *
 * @author leelay
 * @date 2026-07-05
 */
@Component
public class ControlMediaServiceClient {

    private final RestClient restClient;

    /**
     * 创建 ControlMediaServiceClient 实例。
     *
     * @param properties 服务配置
     * @param builder RestClient 构建器
     */
    public ControlMediaServiceClient(ControlProperties properties, RestClient.Builder builder) {
        this.restClient = builder.baseUrl(properties.getMediaServiceBaseUrl()).build();
    }

    /**
     * 请求 Media Service 创建或复用视频会话。
     *
     * @param request 请求参数
     * @param user 当前用户
     * @return 视频会话响应
     */
    public VideoSessionResponse createVideoSession(CreateVideoSessionRequest request, CurrentUser user) {
        return post("/internal/media/video-sessions", request, user, VideoSessionResponse.class);
    }

    /**
     * 请求 Media Service 创建或复用对讲会话。
     *
     * @param request 请求参数
     * @param user 当前用户
     * @return 对讲会话响应
     */
    public IntercomResponse createIntercom(CreateVideoSessionRequest request, CurrentUser user) {
        return post("/internal/media/video-sessions/intercom", request, user, IntercomResponse.class);
    }

    /**
     * 查询最近视频会话。
     *
     * @param user 当前用户
     * @return 最近视频会话列表
     */
    public List<VideoSessionResponse> recent(CurrentUser user) {
        return getList("/internal/media/video-sessions", user, new ParameterizedTypeReference<>() {});
    }

    /**
     * 查询活跃视频会话。
     *
     * @return 活跃视频会话列表
     */
    public List<VideoSessionResponse> active() {
        return getList("/internal/media/video-sessions/active", new ParameterizedTypeReference<>() {});
    }

    /**
     * 查询需要恢复的中断会话。
     *
     * @param before 时间阈值
     * @return 会话 ID 列表
     */
    public List<String> interruptedRestartCandidates(OffsetDateTime before) {
        return getList(
                "/internal/media/video-sessions/interrupted-restart-candidates?before={before}",
                new ParameterizedTypeReference<>() {},
                before);
    }

    /**
     * 查询需要释放的空闲会话。
     *
     * @param before 时间阈值
     * @return 会话 ID 列表
     */
    public List<String> idleReleaseCandidates(OffsetDateTime before) {
        return getList(
                "/internal/media/video-sessions/idle-release-candidates?before={before}",
                new ParameterizedTypeReference<>() {},
                before);
    }

    /**
     * 查询对讲超时会话。
     *
     * @param before 时间阈值
     * @return 会话 ID 列表
     */
    public List<String> intercomTimeoutCandidates(OffsetDateTime before) {
        return getList(
                "/internal/media/video-sessions/intercom-timeout-candidates?before={before}",
                new ParameterizedTypeReference<>() {},
                before);
    }

    /**
     * 查询指定资源。
     *
     * @param sessionId 会话 ID
     * @param user 当前用户
     * @return 查询结果
     */
    public VideoSessionResponse get(String sessionId, CurrentUser user) {
        return get("/internal/media/video-sessions/{sessionId}", user, VideoSessionResponse.class, sessionId);
    }

    /**
     * 查询视频会话事件。
     *
     * @param sessionId 会话 ID
     * @return 事件列表
     */
    public List<MediaEventLogResponse> events(String sessionId) {
        return getList("/internal/media/video-sessions/{sessionId}/events", new ParameterizedTypeReference<>() {}, sessionId);
    }

    /**
     * 查询视频会话 Track。
     *
     * @param sessionId 会话 ID
     * @return Track 列表
     */
    public List<MediaTrackResponse> tracks(String sessionId) {
        return getList("/internal/media/video-sessions/{sessionId}/tracks", new ParameterizedTypeReference<>() {}, sessionId);
    }

    /**
     * 签发观看者 Token。
     *
     * @param sessionId 会话 ID
     * @param user 当前用户
     * @return 观看 Token 响应
     */
    public ViewerTokenResponse token(String sessionId, CurrentUser user) {
        return post("/internal/media/video-sessions/{sessionId}/token", null, user, ViewerTokenResponse.class, sessionId);
    }

    /**
     * 启动对讲。
     *
     * @param sessionId 会话 ID
     * @param user 当前用户
     * @return 对讲会话响应
     */
    public IntercomResponse startIntercom(String sessionId, CurrentUser user) {
        return post("/internal/media/video-sessions/{sessionId}/intercom/start", null, user, IntercomResponse.class, sessionId);
    }

    /**
     * 签发对讲 Token。
     *
     * @param sessionId 会话 ID
     * @param user 当前用户
     * @return 对讲 Token 响应
     */
    public IntercomResponse intercomToken(String sessionId, CurrentUser user) {
        return post("/internal/media/video-sessions/{sessionId}/intercom/token", null, user, IntercomResponse.class, sessionId);
    }

    /**
     * 刷新对讲心跳。
     *
     * @param sessionId 会话 ID
     * @param user 当前用户
     * @return 对讲心跳响应
     */
    public IntercomResponse intercomHeartbeat(String sessionId, CurrentUser user) {
        return post("/internal/media/video-sessions/{sessionId}/intercom/heartbeat", null, user, IntercomResponse.class, sessionId);
    }

    /**
     * 停止对讲。
     *
     * @param sessionId 会话 ID
     * @param user 当前用户
     * @return 视频会话响应
     */
    public VideoSessionResponse stopIntercom(String sessionId, CurrentUser user) {
        return post("/internal/media/video-sessions/{sessionId}/intercom/stop", null, user, VideoSessionResponse.class, sessionId);
    }

    /**
     * 将超时对讲标记为过期。
     *
     * @param sessionId 会话 ID
     * @return 过期处理结果
     */
    public Map<String, Object> expireIntercom(String sessionId) {
        return post("/internal/media/video-sessions/{sessionId}/intercom/expire", null, null, new ParameterizedTypeReference<>() {}, sessionId);
    }

    /**
     * 生成对讲启动 MQTT 命令。
     *
     * @param sessionId 会话 ID
     * @return 对讲启动命令
     */
    public IntercomStartCommand intercomStartCommand(String sessionId) {
        return post("/internal/media/video-sessions/{sessionId}/intercom/start-command", null, null, IntercomStartCommand.class, sessionId);
    }

    /**
     * 刷新观看者心跳。
     *
     * @param sessionId 会话 ID
     * @param user 当前用户
     * @return 视频会话响应
     */
    public VideoSessionResponse heartbeat(String sessionId, CurrentUser user) {
        return post("/internal/media/video-sessions/{sessionId}/heartbeat", null, user, VideoSessionResponse.class, sessionId);
    }

    /**
     * 停止当前观看者或会话。
     *
     * @param sessionId 会话 ID
     * @param user 当前用户
     * @return 视频会话响应
     */
    public VideoSessionResponse stop(String sessionId, CurrentUser user) {
        return post("/internal/media/video-sessions/{sessionId}/stop", null, user, VideoSessionResponse.class, sessionId);
    }

    /**
     * 切换视频通道。
     *
     * @param sessionId 会话 ID
     * @param request 请求参数
     * @return 视频会话响应
     */
    public VideoSessionResponse switchChannel(String sessionId, SwitchChannelRequest request) {
        return post("/internal/media/video-sessions/{sessionId}/switch-channel", request, null, VideoSessionResponse.class, sessionId);
    }

    /**
     * 向 Media Service 转发简单文件上传。
     *
     * @param file 上传文件
     * @param fileType 文件类型
     * @param robotId 机器人 ID
     * @param deviceId 设备 ID
     * @param extensionId 通用扩展 ID
     * @param sourceFileId 源文件 ID
     * @param metadata 扩展元数据
     * @param user 当前用户
     * @return 文件信息
     */
    public FileListItemResponse uploadSimpleFile(
            MultipartFile file,
            FileType fileType,
            String robotId,
            String deviceId,
            String extensionId,
            String sourceFileId,
            String metadata,
            CurrentUser user) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        addPart(body, "fileType", fileType.name());
        addPart(body, "robotId", robotId);
        addPart(body, "deviceId", deviceId);
        addPart(body, "extensionId", extensionId);
        addPart(body, "sourceFileId", sourceFileId);
        addPart(body, "metadata", metadata);
        try {
            body.add("file", new ByteArrayResource(file.getBytes()) {
                /**
                 * 返回上传文件名。
                 *
                 * @return 上传文件名
                 */
                @Override
                public String getFilename() {
                    return file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
                }
            });
        } catch (Exception ex) {
            throw new IllegalStateException("读取上传文件失败", ex);
        }
        return withHeaders(restClient.post()
                        .uri("/internal/media/files")
                        .contentType(MediaType.MULTIPART_FORM_DATA), user)
                .body(body)
                .retrieve()
                .body(FileListItemResponse.class);
    }

    /**
     * 开始直播录像。
     *
     * @param sessionId 会话 ID
     * @param user 当前用户
     * @return 录像文件信息
     */
    public FileListItemResponse startLiveRecording(String sessionId, CurrentUser user) {
        return post("/internal/media/video-sessions/{sessionId}/recordings/start", null, user, FileListItemResponse.class, sessionId);
    }

    /**
     * 停止直播录像。
     *
     * @param sessionId 会话 ID
     * @param fileId 文件 ID
     * @param user 当前用户
     * @return 录像文件信息
     */
    public FileListItemResponse stopLiveRecording(String sessionId, String fileId, CurrentUser user) {
        return post("/internal/media/video-sessions/{sessionId}/recordings/{fileId}/stop",
                null,
                user,
                FileListItemResponse.class,
                sessionId,
                fileId);
    }

    /**
     * 查询当前活跃直播录像。
     *
     * @param sessionId 会话 ID
     * @param user 当前用户
     * @return 活跃录像文件信息
     */
    public FileListItemResponse activeLiveRecording(String sessionId, CurrentUser user) {
        return get("/internal/media/video-sessions/{sessionId}/recordings/active", user, FileListItemResponse.class, sessionId);
    }

    /**
     * 分页查询媒体文件列表。
     *
     * @param robotId 机器人 ID
     * @param deviceId 设备 ID
     * @param extensionId 通用扩展 ID
     * @param fileType 文件类型
     * @param status 状态消息
     * @param page page
     * @param size size
     * @param user 当前用户
     * @return 分页文件列表
     */
    public FileListResponse files(
            String robotId,
            String deviceId,
            String extensionId,
            FileType fileType,
            FileStatus status,
            int page,
            int size,
            CurrentUser user) {
        String uri = UriComponentsBuilder.fromPath("/internal/media/files")
                .queryParamIfPresent("robotId", optional(robotId))
                .queryParamIfPresent("deviceId", optional(deviceId))
                .queryParamIfPresent("extensionId", optional(extensionId))
                .queryParamIfPresent("fileType", Optional.ofNullable(fileType).map(Enum::name))
                .queryParamIfPresent("status", Optional.ofNullable(status).map(Enum::name))
                .queryParam("page", page)
                .queryParam("size", size)
                .build()
                .toUriString();
        return get(uri, user, FileListResponse.class);
    }

    /**
     * 查询文件详情。
     *
     * @param fileId 文件 ID
     * @param user 当前用户
     * @return 文件详情
     */
    public FileListItemResponse file(String fileId, CurrentUser user) {
        return get("/internal/media/files/{fileId}", user, FileListItemResponse.class, fileId);
    }

    /**
     * 生成文件下载地址。
     *
     * @param fileId 文件 ID
     * @param user 当前用户
     * @return 下载地址响应
     */
    public FileDownloadUrlResponse fileDownloadUrl(String fileId, CurrentUser user) {
        return post("/internal/media/files/{fileId}/download-url", null, user, FileDownloadUrlResponse.class, fileId);
    }

    /**
     * 读取文件内容。
     *
     * @param fileId 文件 ID
     * @param user 当前用户
     * @return 文件内容响应
     */
    public ResponseEntity<byte[]> fileContent(String fileId, CurrentUser user) {
        return withHeaders(restClient.get().uri("/internal/media/files/{fileId}/content", fileId), user)
                .retrieve()
                .toEntity(byte[].class);
    }

    /**
     * 生成文件播放地址。
     *
     * @param fileId 文件 ID
     * @param user 当前用户
     * @return 播放地址响应
     */
    public FilePlayUrlResponse filePlayUrl(String fileId, CurrentUser user) {
        return post("/internal/media/files/{fileId}/play-url", null, user, FilePlayUrlResponse.class, fileId);
    }

    /**
     * 读取 HLS 分片或索引资源。
     *
     * @param fileId 文件 ID
     * @param objectName 对象名称
     * @param token 访问令牌
     * @return HLS 资源字节
     */
    public byte[] fileHlsAsset(String fileId, String objectName, String token) {
        return restClient.get()
                .uri("/internal/media/files/{fileId}/hls/{objectName}?token={token}", fileId, objectName, token)
                .retrieve()
                .body(byte[].class);
    }

    /**
     * 请求 Media Service 准备客户端推流命令。
     *
     * @param sessionId 会话 ID
     * @param event 事件名称
     * @return 视频启动命令
     */
    public VideoStartCommand requestClientStart(String sessionId, String event) {
        return post("/internal/media/video-sessions/{sessionId}/client-start?event={event}", null, null, VideoStartCommand.class, sessionId, event);
    }

    /**
     * 生成恢复推流命令。
     *
     * @param sessionId 会话 ID
     * @param user 当前用户
     * @return 视频启动命令
     */
    public VideoStartCommand restartCommand(String sessionId, CurrentUser user) {
        return post("/internal/media/video-sessions/{sessionId}/restart-command", null, user, VideoStartCommand.class, sessionId);
    }

    /**
     * 查询当前会话的启动命令。
     *
     * @param sessionId 会话 ID
     * @return 视频启动命令
     */
    public VideoStartCommand currentStartCommand(String sessionId) {
        return post("/internal/media/video-sessions/{sessionId}/start-command", null, null, VideoStartCommand.class, sessionId);
    }

    /**
     * 查询机器人上线后需要恢复的推流命令。
     *
     * @param robotId 机器人 ID
     * @param status 状态消息
     * @return 视频启动命令列表
     */
    public List<VideoStartCommand> onlineRestartCommands(String robotId, String status) {
        return post(
                "/internal/media/video-sessions/online-restart-commands?robotId={robotId}&status={status}",
                null,
                null,
                new ParameterizedTypeReference<>() {},
                robotId,
                status);
    }

    /**
     * 写回机器人视频状态。
     *
     * @param status 状态消息
     */
    public void updateVideoStatus(VideoStatusMessage status) {
        post("/internal/media/video-sessions/status", status, null, Void.class);
    }

    /**
     * 写回机器人对讲状态。
     *
     * @param status 状态消息
     */
    public void updateIntercomStatus(IntercomStatusMessage status) {
        post("/internal/media/video-sessions/intercom/status", status, null, Void.class);
    }

    /**
     * 释放空闲媒体会话。
     *
     * @param sessionId 会话 ID
     * @return 释放结果
     */
    public Map<String, Object> releaseIdle(String sessionId) {
        return post("/internal/media/video-sessions/{sessionId}/release-idle", null, null, new ParameterizedTypeReference<>() {}, sessionId);
    }

    /**
     * 查询指定资源。
     *
     * @param uri 请求 URI
     * @param user 当前用户
     * @param responseType 响应类型
     * @param uriVariables URI 变量
     * @return 查询结果
     */
    private <T> T get(String uri, CurrentUser user, Class<T> responseType, Object... uriVariables) {
        return withHeaders(restClient.get().uri(uri, uriVariables), user)
                .retrieve()
                .body(responseType);
    }

    /**
     * 返回响应列表。
     *
     * @param uri 请求 URI
     * @param responseType 响应类型
     * @param uriVariables URI 变量
     * @return 响应列表
     */
    private <T> List<T> getList(String uri, ParameterizedTypeReference<List<T>> responseType, Object... uriVariables) {
        return restClient.get().uri(uri, uriVariables).retrieve().body(responseType);
    }

    /**
     * 返回响应列表。
     *
     * @param uri 请求 URI
     * @param user 当前用户
     * @param responseType 响应类型
     * @param uriVariables URI 变量
     * @return 响应列表
     */
    private <T> List<T> getList(String uri, CurrentUser user, ParameterizedTypeReference<List<T>> responseType, Object... uriVariables) {
        return withHeaders(restClient.get().uri(uri, uriVariables), user)
                .retrieve()
                .body(responseType);
    }

    /**
     * 向 Media Service 发送 POST 请求。
     *
     * @param uri 请求 URI
     * @param body 请求体
     * @param user 当前用户
     * @param responseType 响应类型
     * @param uriVariables URI 变量
     * @return 响应对象
     */
    private <T> T post(String uri, Object body, CurrentUser user, Class<T> responseType, Object... uriVariables) {
        RestClient.RequestBodySpec spec = withHeaders(restClient.post().uri(uri, uriVariables), user);
        return (body == null ? spec : spec.body(body)).retrieve().body(responseType);
    }

    /**
     * 向 Media Service 发送 POST 请求。
     *
     * @param uri 请求 URI
     * @param body 请求体
     * @param user 当前用户
     * @param responseType 响应类型
     * @param uriVariables URI 变量
     * @return 响应对象
     */
    private <T> T post(String uri, Object body, CurrentUser user, ParameterizedTypeReference<T> responseType, Object... uriVariables) {
        RestClient.RequestBodySpec spec = withHeaders(restClient.post().uri(uri, uriVariables), user);
        return (body == null ? spec : spec.body(body)).retrieve().body(responseType);
    }

    /**
     * 为服务间请求补充用户上下文请求头。
     *
     * @param spec 请求构造对象
     * @param user 当前用户
     * @return 带请求头的请求构造对象
     */
    private RestClient.RequestBodySpec withHeaders(RestClient.RequestBodySpec spec, CurrentUser user) {
        if (user == null) {
            return spec;
        }
        return spec
                .header("X-User-Id", user.userId())
                .header("X-Org-Id", user.orgId())
                .header("X-Roles", String.join(",", user.roles()))
                .header("X-Client-Id", user.clientId());
    }

    /**
     * 为服务间请求补充用户上下文请求头。
     *
     * @param spec 请求构造对象
     * @param user 当前用户
     * @return 带请求头的请求构造对象
     */
    private RestClient.RequestHeadersSpec<?> withHeaders(RestClient.RequestHeadersSpec<?> spec, CurrentUser user) {
        if (user == null) {
            return spec;
        }
        return spec
                .header("X-User-Id", user.userId())
                .header("X-Org-Id", user.orgId())
                .header("X-Roles", String.join(",", user.roles()))
                .header("X-Client-Id", user.clientId());
    }

    /**
     * 向 multipart 请求体添加非空字段。
     *
     * @param body 请求体
     * @param key 字段名
     * @param value 待处理值
     */
    private void addPart(MultiValueMap<String, Object> body, String key, Object value) {
        if (value != null) {
            body.add(key, value);
        }
    }

    /**
     * 将空白字符串转换为空 Optional。
     *
     * @param value 待处理值
     * @return 可选字符串
     */
    private Optional<String> optional(String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }
}
