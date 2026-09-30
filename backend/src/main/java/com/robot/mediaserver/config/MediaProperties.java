package com.robot.mediaserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 媒体服务配置属性。
 *
 * <p>所有外部中间件地址、账号和运行策略都从 application.yml 或环境变量注入，
 * 避免在业务代码中硬编码部署信息。</p>
 *
 * @author leelay
 * @date 2026/05/19
 */
@ConfigurationProperties(prefix = "media")
public class MediaProperties {

    /**
     * LiveKit 地址、服务凭据、令牌和接入策略配置。
     */
    private Livekit livekit = new Livekit();
    /**
     * 对象存储地址、凭据及桶配置。
     */
    private Minio minio = new Minio();
    /**
     * 媒体会话超时、释放及后台维护配置
     */
    private Session session = new Session();
    /**
     * 通用文件上传、存储、播放与清理配置
     */
    private File file = new File();
    /**
     * 语音合成、缓存和广播配置。
     */
    private Tts tts = new Tts();

    /**
     * 读取{@link #livekit}。
     *
     * @return 当前值，含义与约束见{@link #livekit}
     */
    public Livekit getLivekit() {
        return livekit;
    }

    /**
     * 更新{@link #livekit}。
     *
     * @param livekit 新值，含义与约束见{@link #livekit}
     */
    public void setLivekit(Livekit livekit) {
        this.livekit = livekit;
    }

    /**
     * 读取{@link #minio}。
     *
     * @return 当前值，含义与约束见{@link #minio}
     */
    public Minio getMinio() {
        return minio;
    }

    /**
     * 更新{@link #minio}。
     *
     * @param minio 新值，含义与约束见{@link #minio}
     */
    public void setMinio(Minio minio) {
        this.minio = minio;
    }

    /**
     * 读取{@link #session}。
     *
     * @return 当前值，含义与约束见{@link #session}
     */
    public Session getSession() {
        return session;
    }

    /**
     * 更新{@link #session}。
     *
     * @param session 新值，含义与约束见{@link #session}
     */
    public void setSession(Session session) {
        this.session = session;
    }

    /**
     * 读取{@link #file}。
     *
     * @return 当前值，含义与约束见{@link #file}
     */
    public File getFile() {
        return file;
    }

    /**
     * 更新{@link #file}。
     *
     * @param file 新值，含义与约束见{@link #file}
     */
    public void setFile(File file) {
        this.file = file;
    }

    /**
     * 读取{@link #tts}。
     *
     * @return 当前值，含义与约束见{@link #tts}
     */
    public Tts getTts() {
        return tts;
    }

    /**
     * 更新{@link #tts}。
     *
     * @param tts 新值，含义与约束见{@link #tts}
     */
    public void setTts(Tts tts) {
        this.tts = tts;
    }

    /** LiveKit 地址、服务凭证、令牌期限及 Ingress 调用预算配置。 */
    public static class Livekit {
    /**
     * 浏览器和设备接入 LiveKit 的对外信令地址
     */
    private String url;
    /**
     * 服务端调用 LiveKit 的内部地址配置；未配置时访问器回退到对外 url。
     */
    private String internalUrl;
    /**
     * 服务 API 访问标识。
     */
    private String apiKey;
    /**
     * 服务 API 签名密钥，不得写入日志。
     */
    private String apiSecret;
    /**
     * 签发令牌的有效期，单位秒。
     */
    private long tokenTtlSeconds = 600;
    /** 现场应用 视频呼叫 Token 有效期（默认 2 小时，避免通话中途媒体被踢）。 */
    private long fieldCallTokenTtlSeconds = 7200;
    /**
     * 是否启用 LiveKit Room API 事实查询。
     */
    private boolean roomApiEnabled;
    /**
     * 是否启用 LiveKit 录像导出。
     */
    private boolean egressEnabled;
    /**
     * 是否启用 LiveKit Ingress 管理能力。
     */
    private boolean ingressEnabled;
    /**
     * Ingress 状态快照允许的最大陈旧时长，单位秒。
     */
    private int ingressStatusStaleSeconds = 15;
    /**
     * 单次 LiveKit Ingress 调用的超时，单位毫秒。
     */
    private int ingressLivekitCallTimeoutMs = 3000;
    /**
     * 一次 Ingress 管理操作的总时间预算，单位毫秒。
     */
    private int ingressAdminOperationTimeoutMs = 5000;
    /**
     * 等待 Ingress 管理并发许可的时间上限，单位毫秒。
     */
    private int ingressAdminPermitWaitMs = 200;
    /**
     * 单实例同时执行 Ingress 管理操作的上限。
     */
    private int ingressAdminMaxConcurrency = 2;
    /**
     * LiveKit HLS 导出的目标分片时长，单位秒。
     */
    private int egressSegmentDurationSeconds = 6;
    /**
     * 录像对象存储使用的区域标识。
     */
    private String egressS3Region = "us-east-1";
    /**
     * 是否以路径方式访问录像存储桶。
     */
    private boolean egressS3ForcePathStyle = true;
    /**
     * 房间尚无人加入时的空闲期限，单位秒。
     */
    private int roomEmptyTimeoutSeconds = 60;
    /**
     * 最后参与者离开后保留房间的时长，单位秒。
     */
    private int roomDepartureTimeoutSeconds = 20;

    /**
     * 读取{@link #url}。
     *
     * @return 当前值，含义与约束见{@link #url}
     */
    public String getUrl() {
            return url;
        }

    /**
     * 更新{@link #url}。
     *
     * @param url 新值，含义与约束见{@link #url}
     */
    public void setUrl(String url) {
            this.url = url;
        }

    /**
     * 读取 LiveKit 内部调用地址；未配置时复用对外地址。
     *
     * @return 有效的内部调用地址
     */
    public String getInternalUrl() {
            return internalUrl == null || internalUrl.isBlank() ? url : internalUrl;
        }

    /**
     * 更新{@link #internalUrl}。
     *
     * @param internalUrl 新值，含义与约束见{@link #internalUrl}
     */
    public void setInternalUrl(String internalUrl) {
            this.internalUrl = internalUrl;
        }

    /**
     * 读取{@link #apiKey}。
     *
     * @return 当前值，含义与约束见{@link #apiKey}
     */
    public String getApiKey() {
            return apiKey;
        }

    /**
     * 更新{@link #apiKey}。
     *
     * @param apiKey 新值，含义与约束见{@link #apiKey}
     */
    public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

    /**
     * 读取{@link #apiSecret}。
     *
     * @return 当前值，含义与约束见{@link #apiSecret}
     */
    public String getApiSecret() {
            return apiSecret;
        }

    /**
     * 更新{@link #apiSecret}。
     *
     * @param apiSecret 新值，含义与约束见{@link #apiSecret}
     */
    public void setApiSecret(String apiSecret) {
            this.apiSecret = apiSecret;
        }

    /**
     * 读取{@link #tokenTtlSeconds}。
     *
     * @return 当前值，含义与约束见{@link #tokenTtlSeconds}
     */
    public long getTokenTtlSeconds() {
            return tokenTtlSeconds;
        }

    /**
     * 更新{@link #tokenTtlSeconds}。
     *
     * @param tokenTtlSeconds 新值，含义与约束见{@link #tokenTtlSeconds}
     */
    public void setTokenTtlSeconds(long tokenTtlSeconds) {
            this.tokenTtlSeconds = tokenTtlSeconds;
        }

    /**
     * 读取现场呼叫令牌期限；非正值时回退到通用令牌期限。
     *
     * @return 有效期限，单位秒
     */
    public long getFieldCallTokenTtlSeconds() {
            return fieldCallTokenTtlSeconds > 0 ? fieldCallTokenTtlSeconds : tokenTtlSeconds;
        }

    /**
     * 设置现场应用 视频呼叫 Token 有效期（默认 2 小时，避免通话中途媒体被踢）。。
     *
     * @param fieldCallTokenTtlSeconds 现场应用 视频呼叫 Token 有效期（默认 2 小时，避免通话中途媒体被踢）。
     */
    public void setFieldCallTokenTtlSeconds(long fieldCallTokenTtlSeconds) {
            this.fieldCallTokenTtlSeconds = fieldCallTokenTtlSeconds;
        }

    /**
     * 读取{@link #roomApiEnabled}。
     *
     * @return 当前值，含义与约束见{@link #roomApiEnabled}
     */
    public boolean isRoomApiEnabled() {
            return roomApiEnabled;
        }

    /**
     * 更新{@link #roomApiEnabled}。
     *
     * @param roomApiEnabled 新值，含义与约束见{@link #roomApiEnabled}
     */
    public void setRoomApiEnabled(boolean roomApiEnabled) {
            this.roomApiEnabled = roomApiEnabled;
        }

    /**
     * 读取{@link #egressEnabled}。
     *
     * @return 当前值，含义与约束见{@link #egressEnabled}
     */
    public boolean isEgressEnabled() {
            return egressEnabled;
        }

    /**
     * 更新{@link #egressEnabled}。
     *
     * @param egressEnabled 新值，含义与约束见{@link #egressEnabled}
     */
    public void setEgressEnabled(boolean egressEnabled) {
            this.egressEnabled = egressEnabled;
        }

    /**
     * 读取{@link #ingressEnabled}。
     *
     * @return 当前值，含义与约束见{@link #ingressEnabled}
     */
    public boolean isIngressEnabled() {
            return ingressEnabled;
        }

    /**
     * 更新{@link #ingressEnabled}。
     *
     * @param ingressEnabled 新值，含义与约束见{@link #ingressEnabled}
     */
    public void setIngressEnabled(boolean ingressEnabled) {
            this.ingressEnabled = ingressEnabled;
        }

    /**
     * 读取{@link #ingressStatusStaleSeconds}。
     *
     * @return 当前值，含义与约束见{@link #ingressStatusStaleSeconds}
     */
    public int getIngressStatusStaleSeconds() {
            return ingressStatusStaleSeconds;
        }

    /**
     * 更新{@link #ingressStatusStaleSeconds}。
     *
     * @param ingressStatusStaleSeconds 新值，含义与约束见{@link #ingressStatusStaleSeconds}
     */
    public void setIngressStatusStaleSeconds(int ingressStatusStaleSeconds) {
            this.ingressStatusStaleSeconds = ingressStatusStaleSeconds;
        }

    /**
     * 读取{@link #ingressLivekitCallTimeoutMs}。
     *
     * @return 当前值，含义与约束见{@link #ingressLivekitCallTimeoutMs}
     */
    public int getIngressLivekitCallTimeoutMs() {
            return ingressLivekitCallTimeoutMs;
        }

    /**
     * 更新{@link #ingressLivekitCallTimeoutMs}。
     *
     * @param ingressLivekitCallTimeoutMs 新值，含义与约束见{@link #ingressLivekitCallTimeoutMs}
     */
    public void setIngressLivekitCallTimeoutMs(int ingressLivekitCallTimeoutMs) {
            this.ingressLivekitCallTimeoutMs = ingressLivekitCallTimeoutMs;
        }

    /**
     * 读取{@link #ingressAdminOperationTimeoutMs}。
     *
     * @return 当前值，含义与约束见{@link #ingressAdminOperationTimeoutMs}
     */
    public int getIngressAdminOperationTimeoutMs() {
            return ingressAdminOperationTimeoutMs;
        }

    /**
     * 更新{@link #ingressAdminOperationTimeoutMs}。
     *
     * @param ingressAdminOperationTimeoutMs 新值，含义与约束见{@link #ingressAdminOperationTimeoutMs}
     */
    public void setIngressAdminOperationTimeoutMs(int ingressAdminOperationTimeoutMs) {
            this.ingressAdminOperationTimeoutMs = ingressAdminOperationTimeoutMs;
        }

    /**
     * 读取{@link #ingressAdminPermitWaitMs}。
     *
     * @return 当前值，含义与约束见{@link #ingressAdminPermitWaitMs}
     */
    public int getIngressAdminPermitWaitMs() {
            return ingressAdminPermitWaitMs;
        }

    /**
     * 更新{@link #ingressAdminPermitWaitMs}。
     *
     * @param ingressAdminPermitWaitMs 新值，含义与约束见{@link #ingressAdminPermitWaitMs}
     */
    public void setIngressAdminPermitWaitMs(int ingressAdminPermitWaitMs) {
            this.ingressAdminPermitWaitMs = ingressAdminPermitWaitMs;
        }

    /**
     * 读取{@link #ingressAdminMaxConcurrency}。
     *
     * @return 当前值，含义与约束见{@link #ingressAdminMaxConcurrency}
     */
    public int getIngressAdminMaxConcurrency() {
            return ingressAdminMaxConcurrency;
        }

    /**
     * 更新{@link #ingressAdminMaxConcurrency}。
     *
     * @param ingressAdminMaxConcurrency 新值，含义与约束见{@link #ingressAdminMaxConcurrency}
     */
    public void setIngressAdminMaxConcurrency(int ingressAdminMaxConcurrency) {
            this.ingressAdminMaxConcurrency = ingressAdminMaxConcurrency;
        }

    /**
     * 读取{@link #egressSegmentDurationSeconds}。
     *
     * @return 当前值，含义与约束见{@link #egressSegmentDurationSeconds}
     */
    public int getEgressSegmentDurationSeconds() {
            return egressSegmentDurationSeconds;
        }

    /**
     * 更新{@link #egressSegmentDurationSeconds}。
     *
     * @param egressSegmentDurationSeconds 新值，含义与约束见{@link #egressSegmentDurationSeconds}
     */
    public void setEgressSegmentDurationSeconds(int egressSegmentDurationSeconds) {
            this.egressSegmentDurationSeconds = egressSegmentDurationSeconds;
        }

    /**
     * 读取{@link #egressS3Region}。
     *
     * @return 当前值，含义与约束见{@link #egressS3Region}
     */
    public String getEgressS3Region() {
            return egressS3Region;
        }

    /**
     * 更新{@link #egressS3Region}。
     *
     * @param egressS3Region 新值，含义与约束见{@link #egressS3Region}
     */
    public void setEgressS3Region(String egressS3Region) {
            this.egressS3Region = egressS3Region;
        }

    /**
     * 读取{@link #egressS3ForcePathStyle}。
     *
     * @return 当前值，含义与约束见{@link #egressS3ForcePathStyle}
     */
    public boolean isEgressS3ForcePathStyle() {
            return egressS3ForcePathStyle;
        }

    /**
     * 更新{@link #egressS3ForcePathStyle}。
     *
     * @param egressS3ForcePathStyle 新值，含义与约束见{@link #egressS3ForcePathStyle}
     */
    public void setEgressS3ForcePathStyle(boolean egressS3ForcePathStyle) {
            this.egressS3ForcePathStyle = egressS3ForcePathStyle;
        }

    /**
     * 读取{@link #roomEmptyTimeoutSeconds}。
     *
     * @return 当前值，含义与约束见{@link #roomEmptyTimeoutSeconds}
     */
    public int getRoomEmptyTimeoutSeconds() {
            return roomEmptyTimeoutSeconds;
        }

    /**
     * 更新{@link #roomEmptyTimeoutSeconds}。
     *
     * @param roomEmptyTimeoutSeconds 新值，含义与约束见{@link #roomEmptyTimeoutSeconds}
     */
    public void setRoomEmptyTimeoutSeconds(int roomEmptyTimeoutSeconds) {
            this.roomEmptyTimeoutSeconds = roomEmptyTimeoutSeconds;
        }

    /**
     * 读取{@link #roomDepartureTimeoutSeconds}。
     *
     * @return 当前值，含义与约束见{@link #roomDepartureTimeoutSeconds}
     */
    public int getRoomDepartureTimeoutSeconds() {
            return roomDepartureTimeoutSeconds;
        }

    /**
     * 更新{@link #roomDepartureTimeoutSeconds}。
     *
     * @param roomDepartureTimeoutSeconds 新值，含义与约束见{@link #roomDepartureTimeoutSeconds}
     */
    public void setRoomDepartureTimeoutSeconds(int roomDepartureTimeoutSeconds) {
            this.roomDepartureTimeoutSeconds = roomDepartureTimeoutSeconds;
        }
    }

    /** 对象存储的内外网端点、区域、桶及访问配置。 */
    public static class Minio {
    /**
     * 对象存储内部访问地址。
     */
    private String endpoint;
    /**
     * 对外对象存储端点。
     */
    private String publicEndpoint;
    /**
     * 下载专用的对象存储外部地址配置；缺省回退规则见 getDownloadPublicEndpoint。
     */
    private String downloadPublicEndpoint;
    /**
     * 对象存储使用的区域标识。
     */
    private String region;
    /**
     * 对象存储访问键，不得写入日志。
     */
    private String accessKey;
    /**
     * 对象存储访问密钥，不得写入日志。
     */
    private String secretKey;
    /**
     * 对象存储桶名称。
     */
    private String bucket;
    /**
     * 是否启用 MinIO 对象存储访问
     */
    private boolean enabled;

    /**
     * 读取{@link #endpoint}。
     *
     * @return 当前值，含义与约束见{@link #endpoint}
     */
    public String getEndpoint() {
            return endpoint;
        }

    /**
     * 更新{@link #endpoint}。
     *
     * @param endpoint 新值，含义与约束见{@link #endpoint}
     */
    public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

    /**
     * 读取预签名地址的对外端点；未配置时回退到存储端点。
     *
     * @return 对外对象存储端点
     */
    public String getPublicEndpoint() {
            return publicEndpoint == null || publicEndpoint.isBlank() ? endpoint : publicEndpoint;
        }

    /**
     * 更新{@link #publicEndpoint}。
     *
     * @param publicEndpoint 新值，含义与约束见{@link #publicEndpoint}
     */
    public void setPublicEndpoint(String publicEndpoint) {
            this.publicEndpoint = publicEndpoint;
        }

    /**
     * 读取下载地址端点，并按配置层级回退。
     *
     * @return 有效的对外下载端点
     */
    public String getDownloadPublicEndpoint() {
            return downloadPublicEndpoint == null || downloadPublicEndpoint.isBlank() ? getPublicEndpoint() : downloadPublicEndpoint;
        }

    /**
     * 更新{@link #downloadPublicEndpoint}。
     *
     * @param downloadPublicEndpoint 新值，含义与约束见{@link #downloadPublicEndpoint}
     */
    public void setDownloadPublicEndpoint(String downloadPublicEndpoint) {
            this.downloadPublicEndpoint = downloadPublicEndpoint;
        }

    /**
     * 读取{@link #region}。
     *
     * @return 当前值，含义与约束见{@link #region}
     */
    public String getRegion() {
            return region;
        }

    /**
     * 更新{@link #region}。
     *
     * @param region 新值，含义与约束见{@link #region}
     */
    public void setRegion(String region) {
            this.region = region;
        }

    /**
     * 读取{@link #accessKey}。
     *
     * @return 当前值，含义与约束见{@link #accessKey}
     */
    public String getAccessKey() {
            return accessKey;
        }

    /**
     * 更新{@link #accessKey}。
     *
     * @param accessKey 新值，含义与约束见{@link #accessKey}
     */
    public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

    /**
     * 读取{@link #secretKey}。
     *
     * @return 当前值，含义与约束见{@link #secretKey}
     */
    public String getSecretKey() {
            return secretKey;
        }

    /**
     * 更新{@link #secretKey}。
     *
     * @param secretKey 新值，含义与约束见{@link #secretKey}
     */
    public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

    /**
     * 读取{@link #bucket}。
     *
     * @return 当前值，含义与约束见{@link #bucket}
     */
    public String getBucket() {
            return bucket;
        }

    /**
     * 更新{@link #bucket}。
     *
     * @param bucket 新值，含义与约束见{@link #bucket}
     */
    public void setBucket(String bucket) {
            this.bucket = bucket;
        }

    /**
     * 读取{@link #enabled}。
     *
     * @return 当前值，含义与约束见{@link #enabled}
     */
    public boolean isEnabled() {
            return enabled;
        }

    /**
     * 更新{@link #enabled}。
     *
     * @param enabled 新值，含义与约束见{@link #enabled}
     */
    public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /** 视频发布、观看端心跳、空闲回收和调度租约的超时配置。 */
    public static class Session {
    /**
     * 等待发布者音视频轨道出现的最长时长，单位秒。
     */
    private long trackPublishTimeoutSeconds = 20;
    /**
     * 无人观看后延迟释放会话的时长，单位秒
     */
    private long idleReleaseDelaySeconds = 30;
    /**
     * 观看端心跳允许的最大间隔，单位秒
     */
    private long viewerHeartbeatTimeoutSeconds = 15;
    /**
     * 跨实例调度租约有效期，单位秒。
     */
    private long schedulerLeaseSeconds = 30;

    /**
     * 读取{@link #trackPublishTimeoutSeconds}。
     *
     * @return 当前值，含义与约束见{@link #trackPublishTimeoutSeconds}
     */
    public long getTrackPublishTimeoutSeconds() {
            return trackPublishTimeoutSeconds;
        }

    /**
     * 更新{@link #trackPublishTimeoutSeconds}。
     *
     * @param trackPublishTimeoutSeconds 新值，含义与约束见{@link #trackPublishTimeoutSeconds}
     */
    public void setTrackPublishTimeoutSeconds(long trackPublishTimeoutSeconds) {
            this.trackPublishTimeoutSeconds = trackPublishTimeoutSeconds;
        }

    /**
     * 读取{@link #idleReleaseDelaySeconds}。
     *
     * @return 当前值，含义与约束见{@link #idleReleaseDelaySeconds}
     */
    public long getIdleReleaseDelaySeconds() {
            return idleReleaseDelaySeconds;
        }

    /**
     * 更新{@link #idleReleaseDelaySeconds}。
     *
     * @param idleReleaseDelaySeconds 新值，含义与约束见{@link #idleReleaseDelaySeconds}
     */
    public void setIdleReleaseDelaySeconds(long idleReleaseDelaySeconds) {
            this.idleReleaseDelaySeconds = idleReleaseDelaySeconds;
        }

    /**
     * 读取{@link #viewerHeartbeatTimeoutSeconds}。
     *
     * @return 当前值，含义与约束见{@link #viewerHeartbeatTimeoutSeconds}
     */
    public long getViewerHeartbeatTimeoutSeconds() {
            return viewerHeartbeatTimeoutSeconds;
        }

    /**
     * 更新{@link #viewerHeartbeatTimeoutSeconds}。
     *
     * @param viewerHeartbeatTimeoutSeconds 新值，含义与约束见{@link #viewerHeartbeatTimeoutSeconds}
     */
    public void setViewerHeartbeatTimeoutSeconds(long viewerHeartbeatTimeoutSeconds) {
            this.viewerHeartbeatTimeoutSeconds = viewerHeartbeatTimeoutSeconds;
        }

    /**
     * 读取{@link #schedulerLeaseSeconds}。
     *
     * @return 当前值，含义与约束见{@link #schedulerLeaseSeconds}
     */
    public long getSchedulerLeaseSeconds() {
            return schedulerLeaseSeconds;
        }

    /**
     * 更新{@link #schedulerLeaseSeconds}。
     *
     * @param schedulerLeaseSeconds 新值，含义与约束见{@link #schedulerLeaseSeconds}
     */
    public void setSchedulerLeaseSeconds(long schedulerLeaseSeconds) {
            this.schedulerLeaseSeconds = schedulerLeaseSeconds;
        }
    }

    /** 语音合成引擎、输出文件及请求和生成锁的超时配置。 */
    public static class Tts {
    /**
     * 是否启用语音合成与广播功能
     */
    private boolean enabled = true;
    /**
     * 语音合成引擎的 HTTP 地址。
     */
    private String engineUrl = "http://127.0.0.1:5500/api/tts";
    /**
     * 语音合成所选发音人标识。
     */
    private String voice = "coqui-tts:zh_baker";
    /**
     * 语音合成请求的音频输出格式
     */
    private String format = "wav";
    /**
     * 语音合成文件的本地存放目录。
     */
    private String outputRoot = "/tmp/robot-media/tts";
    /**
     * 建立下游连接的超时，单位毫秒。
     */
    private int connectTimeoutMs = 5000;
    /**
     * 等待下游响应的超时，单位毫秒。
     */
    private int readTimeoutMs = 30000;
    /**
     * 允许提交的最大语音文本长度。
     */
    private int maxTextLength = 1000;
    /**
     * 等待同内容语音生成锁的超时，单位秒。
     */
    private int generateLockTimeoutSeconds = 30;
    /**
     * 向浏览器发送 PCM 时跳过的 WAV 文件头字节数。
     */
    private int wavHeaderOffset = 44;

    /**
     * 读取{@link #enabled}。
     *
     * @return 当前值，含义与约束见{@link #enabled}
     */
    public boolean isEnabled() {
            return enabled;
        }

    /**
     * 更新{@link #enabled}。
     *
     * @param enabled 新值，含义与约束见{@link #enabled}
     */
    public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

    /**
     * 读取{@link #engineUrl}。
     *
     * @return 当前值，含义与约束见{@link #engineUrl}
     */
    public String getEngineUrl() {
            return engineUrl;
        }

    /**
     * 更新{@link #engineUrl}。
     *
     * @param engineUrl 新值，含义与约束见{@link #engineUrl}
     */
    public void setEngineUrl(String engineUrl) {
            this.engineUrl = engineUrl;
        }

    /**
     * 读取{@link #voice}。
     *
     * @return 当前值，含义与约束见{@link #voice}
     */
    public String getVoice() {
            return voice;
        }

    /**
     * 更新{@link #voice}。
     *
     * @param voice 新值，含义与约束见{@link #voice}
     */
    public void setVoice(String voice) {
            this.voice = voice;
        }

    /**
     * 读取{@link #format}。
     *
     * @return 当前值，含义与约束见{@link #format}
     */
    public String getFormat() {
            return format;
        }

    /**
     * 更新{@link #format}。
     *
     * @param format 新值，含义与约束见{@link #format}
     */
    public void setFormat(String format) {
            this.format = format;
        }

    /**
     * 读取{@link #outputRoot}。
     *
     * @return 当前值，含义与约束见{@link #outputRoot}
     */
    public String getOutputRoot() {
            return outputRoot;
        }

    /**
     * 更新{@link #outputRoot}。
     *
     * @param outputRoot 新值，含义与约束见{@link #outputRoot}
     */
    public void setOutputRoot(String outputRoot) {
            this.outputRoot = outputRoot;
        }

    /**
     * 读取{@link #connectTimeoutMs}。
     *
     * @return 当前值，含义与约束见{@link #connectTimeoutMs}
     */
    public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

    /**
     * 更新{@link #connectTimeoutMs}。
     *
     * @param connectTimeoutMs 新值，含义与约束见{@link #connectTimeoutMs}
     */
    public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

    /**
     * 读取{@link #readTimeoutMs}。
     *
     * @return 当前值，含义与约束见{@link #readTimeoutMs}
     */
    public int getReadTimeoutMs() {
            return readTimeoutMs;
        }

    /**
     * 更新{@link #readTimeoutMs}。
     *
     * @param readTimeoutMs 新值，含义与约束见{@link #readTimeoutMs}
     */
    public void setReadTimeoutMs(int readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
        }

    /**
     * 读取{@link #maxTextLength}。
     *
     * @return 当前值，含义与约束见{@link #maxTextLength}
     */
    public int getMaxTextLength() {
            return maxTextLength;
        }

    /**
     * 更新{@link #maxTextLength}。
     *
     * @param maxTextLength 新值，含义与约束见{@link #maxTextLength}
     */
    public void setMaxTextLength(int maxTextLength) {
            this.maxTextLength = maxTextLength;
        }

    /**
     * 读取{@link #generateLockTimeoutSeconds}。
     *
     * @return 当前值，含义与约束见{@link #generateLockTimeoutSeconds}
     */
    public int getGenerateLockTimeoutSeconds() {
            return generateLockTimeoutSeconds;
        }

    /**
     * 更新{@link #generateLockTimeoutSeconds}。
     *
     * @param generateLockTimeoutSeconds 新值，含义与约束见{@link #generateLockTimeoutSeconds}
     */
    public void setGenerateLockTimeoutSeconds(int generateLockTimeoutSeconds) {
            this.generateLockTimeoutSeconds = generateLockTimeoutSeconds;
        }

    /**
     * 读取{@link #wavHeaderOffset}。
     *
     * @return 当前值，含义与约束见{@link #wavHeaderOffset}
     */
    public int getWavHeaderOffset() {
            return wavHeaderOffset;
        }

    /**
     * 更新{@link #wavHeaderOffset}。
     *
     * @param wavHeaderOffset 新值，含义与约束见{@link #wavHeaderOffset}
     */
    public void setWavHeaderOffset(int wavHeaderOffset) {
            this.wavHeaderOffset = wavHeaderOffset;
        }
    }

    /** 通用文件上传配额、分片、播放签名、处理和保留期配置。 */
    public static class File {
    /**
     * 是否启用通用文件服务
     */
    private boolean enabled = true;
    /**
     * 简单上传允许的最大字节数。
     */
    private long simpleUploadMaxBytes = 20971520L;
    /**
     * 允许上传的最大文件字节数。
     */
    private long maxFileSizeBytes = 51539607552L;
    /**
     * 分片上传的目标分片大小，单位字节。
     */
    private long partSizeBytes = 5242880L;
    /**
     * 单个分片上传允许的最大分片数。
     */
    private int maxPartCount = 10000;
    /**
     * 单次补签请求允许的最大分片 URL 数量。
     */
    private int maxPartUrlsPerRequest = 16;
    /**
     * 创建上传时预签发的分片 URL 数量。
     */
    private int initialPartUrlCount = 16;
    /**
     * 分片上传预签名地址有效期，单位秒。
     */
    private int uploadUrlTtlSeconds = 604800;
    /**
     * 分片上传会话有效期，单位小时。
     */
    private int multipartExpireHours = 720;
    /**
     * 文件播放地址有效期，单位秒。
     */
    private int playUrlTtlSeconds = 3600;
    /**
     * 文件播放令牌签名密钥，不得写入日志。
     */
    private String playTokenSecret = "file-playback-development-secret-change-me";
    /**
     * HLS 转码所用 FFmpeg 可执行文件路径。
     */
    private String hlsFfmpegPath = "ffmpeg";
    /**
     * 媒体信息探测程序路径。
     */
    private String ffprobePath = "ffprobe";
    /**
     * 文件 HLS 后处理的目标分片时长，单位秒。
     */
    private int hlsSegmentDurationSeconds = 6;
    /**
     * 单机器人同时有效的分片上传会话上限。
     */
    private int maxActiveUploadsPerRobot = 100;
    /**
     * 全平台同时有效的分片上传会话上限。
     */
    private int maxActiveUploadsGlobal = 5000;
    /**
     * 单实例同时执行 HLS 后处理的任务上限。
     */
    private int hlsWorkerConcurrency = 2;
    /**
     * HLS 转码超时的基础秒数；实际超时至少为视频时长的两倍，当前不用于跨实例任务租约。
     */
    private int hlsProcessingTimeoutSeconds = 300;
    /** 新名称已绑定非空值后，旧配置别名不得覆盖它，避免绑定顺序影响优先级。 */
    private boolean hlsProcessingTimeoutConfigured;
    /**
     * 文件记录的保留天数。
     */
    private int retentionDays = 30;
    /**
     * 单次手动录像允许的最长时长，单位秒。
     */
    private int liveRecordingMaxDurationSeconds = 14400;
    /**
     * 是否启用机器人来源网络限制。
     */
    private boolean trustedRobotNetworkEnabled;
    /**
     * 允许机器人直连的网络 CIDR 列表。
     */
    private String trustedRobotCidrs = "127.0.0.1/32,::1/128";
    /**
     * 缺少组织上下文或机器人分片上传时使用的默认组织标识；当前代码不按运行环境限制此回退。
     */
    private String defaultOrgId = "org001";
    /**
     * 是否启用上传进度聚合。
     */
    private boolean progressEnabled = true;
    /**
     * 上传进度重建任务的并发上限。
     */
    private int progressRebuildConcurrency = 8;
    /**
     * 分片合并处理租约的有效期，单位秒。
     */
    private int completionLeaseSeconds = 300;
    /**
     * MinIO 进度回调使用的内部凭证，不得写入日志。
     */
    private String progressWebhookToken;

    /**
     * 读取{@link #enabled}。
     *
     * @return 当前值，含义与约束见{@link #enabled}
     */
    public boolean isEnabled() { return enabled; }
    /**
     * 更新{@link #enabled}。
     *
     * @param enabled 新值，含义与约束见{@link #enabled}
     */
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    /**
     * 读取{@link #simpleUploadMaxBytes}。
     *
     * @return 当前值，含义与约束见{@link #simpleUploadMaxBytes}
     */
    public long getSimpleUploadMaxBytes() { return simpleUploadMaxBytes; }
    /**
     * 更新{@link #simpleUploadMaxBytes}。
     *
     * @param simpleUploadMaxBytes 新值，含义与约束见{@link #simpleUploadMaxBytes}
     */
    public void setSimpleUploadMaxBytes(long simpleUploadMaxBytes) { this.simpleUploadMaxBytes = simpleUploadMaxBytes; }
    /**
     * 读取{@link #maxFileSizeBytes}。
     *
     * @return 当前值，含义与约束见{@link #maxFileSizeBytes}
     */
    public long getMaxFileSizeBytes() { return maxFileSizeBytes; }
    /**
     * 更新{@link #maxFileSizeBytes}。
     *
     * @param maxFileSizeBytes 新值，含义与约束见{@link #maxFileSizeBytes}
     */
    public void setMaxFileSizeBytes(long maxFileSizeBytes) { this.maxFileSizeBytes = maxFileSizeBytes; }
    /**
     * 读取{@link #partSizeBytes}。
     *
     * @return 当前值，含义与约束见{@link #partSizeBytes}
     */
    public long getPartSizeBytes() { return partSizeBytes; }
    /**
     * 更新{@link #partSizeBytes}。
     *
     * @param partSizeBytes 新值，含义与约束见{@link #partSizeBytes}
     */
    public void setPartSizeBytes(long partSizeBytes) { this.partSizeBytes = partSizeBytes; }
    /**
     * 读取{@link #maxPartCount}。
     *
     * @return 当前值，含义与约束见{@link #maxPartCount}
     */
    public int getMaxPartCount() { return maxPartCount; }
    /**
     * 更新{@link #maxPartCount}。
     *
     * @param maxPartCount 新值，含义与约束见{@link #maxPartCount}
     */
    public void setMaxPartCount(int maxPartCount) { this.maxPartCount = maxPartCount; }
    /**
     * 读取{@link #maxPartUrlsPerRequest}。
     *
     * @return 当前值，含义与约束见{@link #maxPartUrlsPerRequest}
     */
    public int getMaxPartUrlsPerRequest() { return maxPartUrlsPerRequest; }
    /**
     * 更新{@link #maxPartUrlsPerRequest}。
     *
     * @param maxPartUrlsPerRequest 新值，含义与约束见{@link #maxPartUrlsPerRequest}
     */
    public void setMaxPartUrlsPerRequest(int maxPartUrlsPerRequest) { this.maxPartUrlsPerRequest = maxPartUrlsPerRequest; }
    /**
     * 读取{@link #initialPartUrlCount}。
     *
     * @return 当前值，含义与约束见{@link #initialPartUrlCount}
     */
    public int getInitialPartUrlCount() { return initialPartUrlCount; }
    /**
     * 更新{@link #initialPartUrlCount}。
     *
     * @param initialPartUrlCount 新值，含义与约束见{@link #initialPartUrlCount}
     */
    public void setInitialPartUrlCount(int initialPartUrlCount) { this.initialPartUrlCount = initialPartUrlCount; }
    /**
     * 读取{@link #uploadUrlTtlSeconds}。
     *
     * @return 当前值，含义与约束见{@link #uploadUrlTtlSeconds}
     */
    public int getUploadUrlTtlSeconds() { return uploadUrlTtlSeconds; }
    /**
     * 更新{@link #uploadUrlTtlSeconds}。
     *
     * @param uploadUrlTtlSeconds 新值，含义与约束见{@link #uploadUrlTtlSeconds}
     */
    public void setUploadUrlTtlSeconds(int uploadUrlTtlSeconds) { this.uploadUrlTtlSeconds = uploadUrlTtlSeconds; }
    /**
     * 读取{@link #multipartExpireHours}。
     *
     * @return 当前值，含义与约束见{@link #multipartExpireHours}
     */
    public int getMultipartExpireHours() { return multipartExpireHours; }
    /**
     * 更新{@link #multipartExpireHours}。
     *
     * @param multipartExpireHours 新值，含义与约束见{@link #multipartExpireHours}
     */
    public void setMultipartExpireHours(int multipartExpireHours) { this.multipartExpireHours = multipartExpireHours; }
    /**
     * 读取{@link #playUrlTtlSeconds}。
     *
     * @return 当前值，含义与约束见{@link #playUrlTtlSeconds}
     */
    public int getPlayUrlTtlSeconds() { return playUrlTtlSeconds; }
    /**
     * 更新{@link #playUrlTtlSeconds}。
     *
     * @param playUrlTtlSeconds 新值，含义与约束见{@link #playUrlTtlSeconds}
     */
    public void setPlayUrlTtlSeconds(int playUrlTtlSeconds) { this.playUrlTtlSeconds = playUrlTtlSeconds; }
    /**
     * 读取{@link #playTokenSecret}。
     *
     * @return 当前值，含义与约束见{@link #playTokenSecret}
     */
    public String getPlayTokenSecret() { return playTokenSecret; }
    /**
     * 更新{@link #playTokenSecret}。
     *
     * @param playTokenSecret 新值，含义与约束见{@link #playTokenSecret}
     */
    public void setPlayTokenSecret(String playTokenSecret) { this.playTokenSecret = playTokenSecret; }
    /**
     * 读取{@link #hlsFfmpegPath}。
     *
     * @return 当前值，含义与约束见{@link #hlsFfmpegPath}
     */
    public String getHlsFfmpegPath() { return hlsFfmpegPath; }
    /**
     * 更新{@link #hlsFfmpegPath}。
     *
     * @param hlsFfmpegPath 新值，含义与约束见{@link #hlsFfmpegPath}
     */
    public void setHlsFfmpegPath(String hlsFfmpegPath) { this.hlsFfmpegPath = hlsFfmpegPath; }
    /**
     * 读取{@link #ffprobePath}。
     *
     * @return 当前值，含义与约束见{@link #ffprobePath}
     */
    public String getFfprobePath() { return ffprobePath; }
    /**
     * 更新{@link #ffprobePath}。
     *
     * @param ffprobePath 新值，含义与约束见{@link #ffprobePath}
     */
    public void setFfprobePath(String ffprobePath) { this.ffprobePath = ffprobePath; }
    /**
     * 读取{@link #hlsSegmentDurationSeconds}。
     *
     * @return 当前值，含义与约束见{@link #hlsSegmentDurationSeconds}
     */
    public int getHlsSegmentDurationSeconds() { return hlsSegmentDurationSeconds; }
    /**
     * 更新{@link #hlsSegmentDurationSeconds}。
     *
     * @param hlsSegmentDurationSeconds 新值，含义与约束见{@link #hlsSegmentDurationSeconds}
     */
    public void setHlsSegmentDurationSeconds(int hlsSegmentDurationSeconds) { this.hlsSegmentDurationSeconds = hlsSegmentDurationSeconds; }
    /**
     * 读取{@link #maxActiveUploadsPerRobot}。
     *
     * @return 当前值，含义与约束见{@link #maxActiveUploadsPerRobot}
     */
    public int getMaxActiveUploadsPerRobot() { return maxActiveUploadsPerRobot; }
    /**
     * 更新{@link #maxActiveUploadsPerRobot}。
     *
     * @param maxActiveUploadsPerRobot 新值，含义与约束见{@link #maxActiveUploadsPerRobot}
     */
    public void setMaxActiveUploadsPerRobot(int maxActiveUploadsPerRobot) { this.maxActiveUploadsPerRobot = maxActiveUploadsPerRobot; }
    /**
     * 读取{@link #maxActiveUploadsGlobal}。
     *
     * @return 当前值，含义与约束见{@link #maxActiveUploadsGlobal}
     */
    public int getMaxActiveUploadsGlobal() { return maxActiveUploadsGlobal; }
    /**
     * 更新{@link #maxActiveUploadsGlobal}。
     *
     * @param maxActiveUploadsGlobal 新值，含义与约束见{@link #maxActiveUploadsGlobal}
     */
    public void setMaxActiveUploadsGlobal(int maxActiveUploadsGlobal) { this.maxActiveUploadsGlobal = maxActiveUploadsGlobal; }
    /**
     * 读取{@link #hlsWorkerConcurrency}。
     *
     * @return 当前值，含义与约束见{@link #hlsWorkerConcurrency}
     */
    public int getHlsWorkerConcurrency() { return hlsWorkerConcurrency; }
    /**
     * 更新{@link #hlsWorkerConcurrency}。
     *
     * @param hlsWorkerConcurrency 新值，含义与约束见{@link #hlsWorkerConcurrency}
     */
    public void setHlsWorkerConcurrency(int hlsWorkerConcurrency) { this.hlsWorkerConcurrency = hlsWorkerConcurrency; }
    /**
     * 读取{@link #hlsProcessingTimeoutSeconds}。
     *
     * @return 当前值，含义与约束见{@link #hlsProcessingTimeoutSeconds}
     */
    public Integer getHlsProcessingTimeoutSeconds() { return hlsProcessingTimeoutSeconds; }
    /**
     * 更新{@link #hlsProcessingTimeoutSeconds}。
     *
     * @param hlsProcessingTimeoutSeconds 新值，含义与约束见{@link #hlsProcessingTimeoutSeconds}；空值按未配置处理
     */
    public void setHlsProcessingTimeoutSeconds(Integer hlsProcessingTimeoutSeconds) {
        // Spring 将空配置转换为 null；保留已有旧值或默认值，不让空的新名称阻断回退。
        if (hlsProcessingTimeoutSeconds != null) {
            this.hlsProcessingTimeoutSeconds = hlsProcessingTimeoutSeconds;
            this.hlsProcessingTimeoutConfigured = true;
        }
    }
    /**
     * 为旧配置名称提供访问器，内部仍使用唯一的转码超时值。
     *
     * @return 当前转码超时的基础秒数
     * @deprecated 改用 {@link #getHlsProcessingTimeoutSeconds()}，此值不是任务租约。
     */
    @Deprecated
    public Integer getHlsProcessingLeaseSeconds() { return hlsProcessingTimeoutSeconds; }
    /**
     * 兼容旧版外部配置文件；只有新名称尚未绑定时才接受旧值。
     *
     * @param hlsProcessingLeaseSeconds 旧配置键提供的转码超时基础秒数；空值保留默认值
     * @deprecated 改用 {@link #setHlsProcessingTimeoutSeconds(Integer)}。
     */
    @Deprecated
    public void setHlsProcessingLeaseSeconds(Integer hlsProcessingLeaseSeconds) {
        if (!hlsProcessingTimeoutConfigured && hlsProcessingLeaseSeconds != null) {
            this.hlsProcessingTimeoutSeconds = hlsProcessingLeaseSeconds;
        }
    }
    /**
     * 读取{@link #retentionDays}。
     *
     * @return 当前值，含义与约束见{@link #retentionDays}
     */
    public int getRetentionDays() { return retentionDays; }
    /**
     * 更新{@link #retentionDays}。
     *
     * @param retentionDays 新值，含义与约束见{@link #retentionDays}
     */
    public void setRetentionDays(int retentionDays) { this.retentionDays = retentionDays; }
    /**
     * 读取{@link #liveRecordingMaxDurationSeconds}。
     *
     * @return 当前值，含义与约束见{@link #liveRecordingMaxDurationSeconds}
     */
    public int getLiveRecordingMaxDurationSeconds() { return liveRecordingMaxDurationSeconds; }
    /**
     * 更新{@link #liveRecordingMaxDurationSeconds}。
     *
     * @param liveRecordingMaxDurationSeconds 新值，含义与约束见{@link #liveRecordingMaxDurationSeconds}
     */
    public void setLiveRecordingMaxDurationSeconds(int liveRecordingMaxDurationSeconds) { this.liveRecordingMaxDurationSeconds = liveRecordingMaxDurationSeconds; }
    /**
     * 读取{@link #trustedRobotNetworkEnabled}。
     *
     * @return 当前值，含义与约束见{@link #trustedRobotNetworkEnabled}
     */
    public boolean isTrustedRobotNetworkEnabled() { return trustedRobotNetworkEnabled; }
    /**
     * 更新{@link #trustedRobotNetworkEnabled}。
     *
     * @param trustedRobotNetworkEnabled 新值，含义与约束见{@link #trustedRobotNetworkEnabled}
     */
    public void setTrustedRobotNetworkEnabled(boolean trustedRobotNetworkEnabled) { this.trustedRobotNetworkEnabled = trustedRobotNetworkEnabled; }
    /**
     * 读取{@link #trustedRobotCidrs}。
     *
     * @return 当前值，含义与约束见{@link #trustedRobotCidrs}
     */
    public String getTrustedRobotCidrs() { return trustedRobotCidrs; }
    /**
     * 更新{@link #trustedRobotCidrs}。
     *
     * @param trustedRobotCidrs 新值，含义与约束见{@link #trustedRobotCidrs}
     */
    public void setTrustedRobotCidrs(String trustedRobotCidrs) { this.trustedRobotCidrs = trustedRobotCidrs; }
    /**
     * 读取{@link #defaultOrgId}。
     *
     * @return 当前值，含义与约束见{@link #defaultOrgId}
     */
    public String getDefaultOrgId() { return defaultOrgId; }
    /**
     * 更新{@link #defaultOrgId}。
     *
     * @param defaultOrgId 新值，含义与约束见{@link #defaultOrgId}
     */
    public void setDefaultOrgId(String defaultOrgId) { this.defaultOrgId = defaultOrgId; }
    /**
     * 读取{@link #progressEnabled}。
     *
     * @return 当前值，含义与约束见{@link #progressEnabled}
     */
    public boolean isProgressEnabled() { return progressEnabled; }
    /**
     * 更新{@link #progressEnabled}。
     *
     * @param progressEnabled 新值，含义与约束见{@link #progressEnabled}
     */
    public void setProgressEnabled(boolean progressEnabled) { this.progressEnabled = progressEnabled; }
    /**
     * 读取{@link #progressRebuildConcurrency}。
     *
     * @return 当前值，含义与约束见{@link #progressRebuildConcurrency}
     */
    public int getProgressRebuildConcurrency() { return progressRebuildConcurrency; }
    /**
     * 更新{@link #progressRebuildConcurrency}。
     *
     * @param progressRebuildConcurrency 新值，含义与约束见{@link #progressRebuildConcurrency}
     */
    public void setProgressRebuildConcurrency(int progressRebuildConcurrency) { this.progressRebuildConcurrency = progressRebuildConcurrency; }
    /**
     * 读取{@link #completionLeaseSeconds}。
     *
     * @return 当前值，含义与约束见{@link #completionLeaseSeconds}
     */
    public int getCompletionLeaseSeconds() { return completionLeaseSeconds; }
    /**
     * 更新{@link #completionLeaseSeconds}。
     *
     * @param completionLeaseSeconds 新值，含义与约束见{@link #completionLeaseSeconds}
     */
    public void setCompletionLeaseSeconds(int completionLeaseSeconds) { this.completionLeaseSeconds = completionLeaseSeconds; }
    /**
     * 读取{@link #progressWebhookToken}。
     *
     * @return 当前值，含义与约束见{@link #progressWebhookToken}
     */
    public String getProgressWebhookToken() { return progressWebhookToken; }
    /**
     * 更新{@link #progressWebhookToken}。
     *
     * @param progressWebhookToken 新值，含义与约束见{@link #progressWebhookToken}
     */
    public void setProgressWebhookToken(String progressWebhookToken) { this.progressWebhookToken = progressWebhookToken; }
    }
}
