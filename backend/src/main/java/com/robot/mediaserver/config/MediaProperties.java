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

    private Auth auth = new Auth();
    private Livekit livekit = new Livekit();
    private Mqtt mqtt = new Mqtt();
    private Minio minio = new Minio();
    private Rtsp rtsp = new Rtsp();
    private Robot robot = new Robot();
    private Session session = new Session();
    private SnapshotWorker snapshotWorker = new SnapshotWorker();
    private Recording recording = new Recording();

    public Auth getAuth() {
        return auth;
    }

    public void setAuth(Auth auth) {
        this.auth = auth;
    }

    public Livekit getLivekit() {
        return livekit;
    }

    public void setLivekit(Livekit livekit) {
        this.livekit = livekit;
    }

    public Mqtt getMqtt() {
        return mqtt;
    }

    public void setMqtt(Mqtt mqtt) {
        this.mqtt = mqtt;
    }

    public Minio getMinio() {
        return minio;
    }

    public void setMinio(Minio minio) {
        this.minio = minio;
    }

    public Rtsp getRtsp() {
        return rtsp;
    }

    public void setRtsp(Rtsp rtsp) {
        this.rtsp = rtsp;
    }

    public Robot getRobot() {
        return robot;
    }

    public void setRobot(Robot robot) {
        this.robot = robot;
    }

    public Session getSession() {
        return session;
    }

    public void setSession(Session session) {
        this.session = session;
    }

    public SnapshotWorker getSnapshotWorker() {
        return snapshotWorker;
    }

    public void setSnapshotWorker(SnapshotWorker snapshotWorker) {
        this.snapshotWorker = snapshotWorker;
    }

    public Recording getRecording() {
        return recording;
    }

    public void setRecording(Recording recording) {
        this.recording = recording;
    }

    public static class Auth {
    private boolean mockEnabled = true;

    public boolean isMockEnabled() {
            return mockEnabled;
        }

    public void setMockEnabled(boolean mockEnabled) {
            this.mockEnabled = mockEnabled;
        }
    }

    public static class Livekit {
    private String url;
    private String apiKey;
    private String apiSecret;
    private long tokenTtlSeconds = 600;
    private boolean roomApiEnabled;
    private int roomEmptyTimeoutSeconds = 60;
    private int roomDepartureTimeoutSeconds = 20;

    public String getUrl() {
            return url;
        }

    public void setUrl(String url) {
            this.url = url;
        }

    public String getApiKey() {
            return apiKey;
        }

    public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

    public String getApiSecret() {
            return apiSecret;
        }

    public void setApiSecret(String apiSecret) {
            this.apiSecret = apiSecret;
        }

    public long getTokenTtlSeconds() {
            return tokenTtlSeconds;
        }

    public void setTokenTtlSeconds(long tokenTtlSeconds) {
            this.tokenTtlSeconds = tokenTtlSeconds;
        }

    public boolean isRoomApiEnabled() {
            return roomApiEnabled;
        }

    public void setRoomApiEnabled(boolean roomApiEnabled) {
            this.roomApiEnabled = roomApiEnabled;
        }

    public int getRoomEmptyTimeoutSeconds() {
            return roomEmptyTimeoutSeconds;
        }

    public void setRoomEmptyTimeoutSeconds(int roomEmptyTimeoutSeconds) {
            this.roomEmptyTimeoutSeconds = roomEmptyTimeoutSeconds;
        }

    public int getRoomDepartureTimeoutSeconds() {
            return roomDepartureTimeoutSeconds;
        }

    public void setRoomDepartureTimeoutSeconds(int roomDepartureTimeoutSeconds) {
            this.roomDepartureTimeoutSeconds = roomDepartureTimeoutSeconds;
        }
    }

    public static class Mqtt {
    private String brokerUrl;
    private String username;
    private String password;
    private String clientId;
    private boolean enabled;

    public String getBrokerUrl() {
            return brokerUrl;
        }

    public void setBrokerUrl(String brokerUrl) {
            this.brokerUrl = brokerUrl;
        }

    public String getUsername() {
            return username;
        }

    public void setUsername(String username) {
            this.username = username;
        }

    public String getPassword() {
            return password;
        }

    public void setPassword(String password) {
            this.password = password;
        }

    public String getClientId() {
            return clientId;
        }

    public void setClientId(String clientId) {
            this.clientId = clientId;
        }

    public boolean isEnabled() {
            return enabled;
        }

    public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Minio {
    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String bucket;
    private boolean enabled;

    public String getEndpoint() {
            return endpoint;
        }

    public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

    public String getAccessKey() {
            return accessKey;
        }

    public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

    public String getSecretKey() {
            return secretKey;
        }

    public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

    public String getBucket() {
            return bucket;
        }

    public void setBucket(String bucket) {
            this.bucket = bucket;
        }

    public boolean isEnabled() {
            return enabled;
        }

    public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Session {
    private long trackPublishTimeoutSeconds = 20;
    private long interruptedGraceSeconds = 15;
    private long idleReleaseDelaySeconds = 60;
    private long viewerHeartbeatTimeoutSeconds = 15;
    private int maxVideoWallStreams = 16;
    private String maxDetailResolution = "2K";

    public long getTrackPublishTimeoutSeconds() {
            return trackPublishTimeoutSeconds;
        }

    public void setTrackPublishTimeoutSeconds(long trackPublishTimeoutSeconds) {
            this.trackPublishTimeoutSeconds = trackPublishTimeoutSeconds;
        }

    public long getInterruptedGraceSeconds() {
            return interruptedGraceSeconds;
        }

    public void setInterruptedGraceSeconds(long interruptedGraceSeconds) {
            this.interruptedGraceSeconds = interruptedGraceSeconds;
        }

    public long getIdleReleaseDelaySeconds() {
            return idleReleaseDelaySeconds;
        }

    public void setIdleReleaseDelaySeconds(long idleReleaseDelaySeconds) {
            this.idleReleaseDelaySeconds = idleReleaseDelaySeconds;
        }

    public long getViewerHeartbeatTimeoutSeconds() {
            return viewerHeartbeatTimeoutSeconds;
        }

    public void setViewerHeartbeatTimeoutSeconds(long viewerHeartbeatTimeoutSeconds) {
            this.viewerHeartbeatTimeoutSeconds = viewerHeartbeatTimeoutSeconds;
        }

    public int getMaxVideoWallStreams() {
            return maxVideoWallStreams;
        }

    public void setMaxVideoWallStreams(int maxVideoWallStreams) {
            this.maxVideoWallStreams = maxVideoWallStreams;
        }

    public String getMaxDetailResolution() {
            return maxDetailResolution;
        }

    public void setMaxDetailResolution(String maxDetailResolution) {
            this.maxDetailResolution = maxDetailResolution;
        }
    }

    public static class Robot {
    private long heartbeatTimeoutSeconds = 15;

    public long getHeartbeatTimeoutSeconds() {
            return heartbeatTimeoutSeconds;
        }

    public void setHeartbeatTimeoutSeconds(long heartbeatTimeoutSeconds) {
            this.heartbeatTimeoutSeconds = heartbeatTimeoutSeconds;
        }
    }

    public static class Rtsp {
    private String ffprobePath = "ffprobe";
    private String defaultUrl;
    private long timeoutMs = 8000;

    public String getFfprobePath() {
            return ffprobePath;
        }

    public void setFfprobePath(String ffprobePath) {
            this.ffprobePath = ffprobePath;
        }

    public String getDefaultUrl() {
            return defaultUrl;
        }

    public void setDefaultUrl(String defaultUrl) {
            this.defaultUrl = defaultUrl;
        }

    public long getTimeoutMs() {
            return timeoutMs;
        }

    public void setTimeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
        }
    }

    public static class SnapshotWorker {
    private boolean enabled = true;
    private String ffmpegPath = "ffmpeg";
    private long fixedDelayMs = 3000;
    private long timeoutMs = 10000;

    public boolean isEnabled() {
            return enabled;
        }

    public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

    public String getFfmpegPath() {
            return ffmpegPath;
        }

    public void setFfmpegPath(String ffmpegPath) {
            this.ffmpegPath = ffmpegPath;
        }

    public long getFixedDelayMs() {
            return fixedDelayMs;
        }

    public void setFixedDelayMs(long fixedDelayMs) {
            this.fixedDelayMs = fixedDelayMs;
        }

    public long getTimeoutMs() {
            return timeoutMs;
        }

    public void setTimeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
        }
    }

    public static class Recording {
    private boolean enabled = true;
    private long maxFileSizeBytes = 21474836480L;
    private long partSizeBytes = 16777216L;
    private int maxPartUrlsPerRequest = 16;
    private int uploadUrlTtlSeconds = 900;
    private int uploadExpireHours = 72;
    private boolean uploadSessionRefreshEnabled = true;
    private int playUrlTtlSeconds = 3600;
    private String playTokenSecret = "recording-playback-development-secret-change-me";
    private String hlsFfmpegPath = "ffmpeg";
    private String ffprobePath = "ffprobe";
    private int hlsSegmentDurationSeconds = 6;
    private int hlsWorkerConcurrency = 2;
    private int hlsProcessingLeaseSeconds = 300;
    private int maxActiveUploadsPerRobot = 2;
    private int maxActiveUploadsGlobal = 50;
    private int retentionDays = 30;
    private boolean trustedRobotNetworkEnabled;
    private String trustedRobotCidrs = "127.0.0.1/32,::1/128";
    private String defaultOrgId = "org001";

    public boolean isEnabled() {
            return enabled;
        }

    public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

    public long getMaxFileSizeBytes() {
            return maxFileSizeBytes;
        }

    public void setMaxFileSizeBytes(long maxFileSizeBytes) {
            this.maxFileSizeBytes = maxFileSizeBytes;
        }

    public long getPartSizeBytes() {
            return partSizeBytes;
        }

    public void setPartSizeBytes(long partSizeBytes) {
            this.partSizeBytes = partSizeBytes;
        }

    public int getMaxPartUrlsPerRequest() {
            return maxPartUrlsPerRequest;
        }

    public void setMaxPartUrlsPerRequest(int maxPartUrlsPerRequest) {
            this.maxPartUrlsPerRequest = maxPartUrlsPerRequest;
        }

    public int getUploadUrlTtlSeconds() {
            return uploadUrlTtlSeconds;
        }

    public void setUploadUrlTtlSeconds(int uploadUrlTtlSeconds) {
            this.uploadUrlTtlSeconds = uploadUrlTtlSeconds;
        }

    public int getUploadExpireHours() {
            return uploadExpireHours;
        }

    public void setUploadExpireHours(int uploadExpireHours) {
            this.uploadExpireHours = uploadExpireHours;
        }

    public boolean isUploadSessionRefreshEnabled() {
            return uploadSessionRefreshEnabled;
        }

    public void setUploadSessionRefreshEnabled(boolean uploadSessionRefreshEnabled) {
            this.uploadSessionRefreshEnabled = uploadSessionRefreshEnabled;
        }

    public int getPlayUrlTtlSeconds() {
            return playUrlTtlSeconds;
        }

    public void setPlayUrlTtlSeconds(int playUrlTtlSeconds) {
            this.playUrlTtlSeconds = playUrlTtlSeconds;
        }

    public String getPlayTokenSecret() {
            return playTokenSecret;
        }

    public void setPlayTokenSecret(String playTokenSecret) {
            this.playTokenSecret = playTokenSecret;
        }

    public String getHlsFfmpegPath() {
            return hlsFfmpegPath;
        }

    public void setHlsFfmpegPath(String hlsFfmpegPath) {
            this.hlsFfmpegPath = hlsFfmpegPath;
        }

    public String getFfprobePath() {
            return ffprobePath;
        }

    public void setFfprobePath(String ffprobePath) {
            this.ffprobePath = ffprobePath;
        }

    public int getHlsSegmentDurationSeconds() {
            return hlsSegmentDurationSeconds;
        }

    public void setHlsSegmentDurationSeconds(int hlsSegmentDurationSeconds) {
            this.hlsSegmentDurationSeconds = hlsSegmentDurationSeconds;
        }

    public int getHlsWorkerConcurrency() {
            return hlsWorkerConcurrency;
        }

    public void setHlsWorkerConcurrency(int hlsWorkerConcurrency) {
            this.hlsWorkerConcurrency = hlsWorkerConcurrency;
        }

    public int getHlsProcessingLeaseSeconds() {
            return hlsProcessingLeaseSeconds;
        }

    public void setHlsProcessingLeaseSeconds(int hlsProcessingLeaseSeconds) {
            this.hlsProcessingLeaseSeconds = hlsProcessingLeaseSeconds;
        }

    public int getMaxActiveUploadsPerRobot() {
            return maxActiveUploadsPerRobot;
        }

    public void setMaxActiveUploadsPerRobot(int maxActiveUploadsPerRobot) {
            this.maxActiveUploadsPerRobot = maxActiveUploadsPerRobot;
        }

    public int getMaxActiveUploadsGlobal() {
            return maxActiveUploadsGlobal;
        }

    public void setMaxActiveUploadsGlobal(int maxActiveUploadsGlobal) {
            this.maxActiveUploadsGlobal = maxActiveUploadsGlobal;
        }

    public int getRetentionDays() {
            return retentionDays;
        }

    public void setRetentionDays(int retentionDays) {
            this.retentionDays = retentionDays;
        }

    public boolean isTrustedRobotNetworkEnabled() {
            return trustedRobotNetworkEnabled;
        }

    public void setTrustedRobotNetworkEnabled(boolean trustedRobotNetworkEnabled) {
            this.trustedRobotNetworkEnabled = trustedRobotNetworkEnabled;
        }

    public String getTrustedRobotCidrs() {
            return trustedRobotCidrs;
        }

    public void setTrustedRobotCidrs(String trustedRobotCidrs) {
            this.trustedRobotCidrs = trustedRobotCidrs;
        }

    public String getDefaultOrgId() {
            return defaultOrgId;
        }

    public void setDefaultOrgId(String defaultOrgId) {
            this.defaultOrgId = defaultOrgId;
        }
    }
}
