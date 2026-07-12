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

    private Livekit livekit = new Livekit();
    private Minio minio = new Minio();
    private Session session = new Session();
    private File file = new File();
    private Tts tts = new Tts();

    public Livekit getLivekit() {
        return livekit;
    }

    public void setLivekit(Livekit livekit) {
        this.livekit = livekit;
    }

    public Minio getMinio() {
        return minio;
    }

    public void setMinio(Minio minio) {
        this.minio = minio;
    }

    public Session getSession() {
        return session;
    }

    public void setSession(Session session) {
        this.session = session;
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    public Tts getTts() {
        return tts;
    }

    public void setTts(Tts tts) {
        this.tts = tts;
    }

    public static class Livekit {
    private String url;
    private String apiKey;
    private String apiSecret;
    private long tokenTtlSeconds = 600;
    private boolean roomApiEnabled;
    private boolean egressEnabled;
    private int egressSegmentDurationSeconds = 6;
    private String egressS3Region = "us-east-1";
    private boolean egressS3ForcePathStyle = true;
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

    public boolean isEgressEnabled() {
            return egressEnabled;
        }

    public void setEgressEnabled(boolean egressEnabled) {
            this.egressEnabled = egressEnabled;
        }

    public int getEgressSegmentDurationSeconds() {
            return egressSegmentDurationSeconds;
        }

    public void setEgressSegmentDurationSeconds(int egressSegmentDurationSeconds) {
            this.egressSegmentDurationSeconds = egressSegmentDurationSeconds;
        }

    public String getEgressS3Region() {
            return egressS3Region;
        }

    public void setEgressS3Region(String egressS3Region) {
            this.egressS3Region = egressS3Region;
        }

    public boolean isEgressS3ForcePathStyle() {
            return egressS3ForcePathStyle;
        }

    public void setEgressS3ForcePathStyle(boolean egressS3ForcePathStyle) {
            this.egressS3ForcePathStyle = egressS3ForcePathStyle;
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

    public static class Tts {
    private boolean enabled = true;
    private String engineUrl = "http://127.0.0.1:5500/api/tts";
    private String voice = "coqui-tts:zh_baker";
    private String format = "wav";
    private String outputRoot = "/tmp/robot-media/tts";
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 30000;
    private int maxTextLength = 1000;
    private int generateLockTimeoutSeconds = 30;
    private int wavHeaderOffset = 44;

    public boolean isEnabled() {
            return enabled;
        }

    public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

    public String getEngineUrl() {
            return engineUrl;
        }

    public void setEngineUrl(String engineUrl) {
            this.engineUrl = engineUrl;
        }

    public String getVoice() {
            return voice;
        }

    public void setVoice(String voice) {
            this.voice = voice;
        }

    public String getFormat() {
            return format;
        }

    public void setFormat(String format) {
            this.format = format;
        }

    public String getOutputRoot() {
            return outputRoot;
        }

    public void setOutputRoot(String outputRoot) {
            this.outputRoot = outputRoot;
        }

    public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

    public int getReadTimeoutMs() {
            return readTimeoutMs;
        }

    public void setReadTimeoutMs(int readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
        }

    public int getMaxTextLength() {
            return maxTextLength;
        }

    public void setMaxTextLength(int maxTextLength) {
            this.maxTextLength = maxTextLength;
        }

    public int getGenerateLockTimeoutSeconds() {
            return generateLockTimeoutSeconds;
        }

    public void setGenerateLockTimeoutSeconds(int generateLockTimeoutSeconds) {
            this.generateLockTimeoutSeconds = generateLockTimeoutSeconds;
        }

    public int getWavHeaderOffset() {
            return wavHeaderOffset;
        }

    public void setWavHeaderOffset(int wavHeaderOffset) {
            this.wavHeaderOffset = wavHeaderOffset;
        }
    }

    public static class File {
    private boolean enabled = true;
    private long simpleUploadMaxBytes = 20971520L;
    private long maxFileSizeBytes = 21474836480L;
    private long partSizeBytes = 16777216L;
    private int maxPartUrlsPerRequest = 16;
    private int initialPartUrlCount = 16;
    private int uploadUrlTtlSeconds = 900;
    private int multipartExpireHours = 72;
    private int playUrlTtlSeconds = 3600;
    private String playTokenSecret = "file-playback-development-secret-change-me";
    private String hlsFfmpegPath = "ffmpeg";
    private String ffprobePath = "ffprobe";
    private int hlsSegmentDurationSeconds = 6;
    private int maxActiveUploadsPerRobot = 2;
    private int maxActiveUploadsGlobal = 50;
    private int hlsWorkerConcurrency = 2;
    private int hlsProcessingLeaseSeconds = 300;
    private int retentionDays = 30;
    private boolean trustedRobotNetworkEnabled;
    private String trustedRobotCidrs = "127.0.0.1/32,::1/128";
    private String defaultOrgId = "org001";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public long getSimpleUploadMaxBytes() { return simpleUploadMaxBytes; }
    public void setSimpleUploadMaxBytes(long simpleUploadMaxBytes) { this.simpleUploadMaxBytes = simpleUploadMaxBytes; }
    public long getMaxFileSizeBytes() { return maxFileSizeBytes; }
    public void setMaxFileSizeBytes(long maxFileSizeBytes) { this.maxFileSizeBytes = maxFileSizeBytes; }
    public long getPartSizeBytes() { return partSizeBytes; }
    public void setPartSizeBytes(long partSizeBytes) { this.partSizeBytes = partSizeBytes; }
    public int getMaxPartUrlsPerRequest() { return maxPartUrlsPerRequest; }
    public void setMaxPartUrlsPerRequest(int maxPartUrlsPerRequest) { this.maxPartUrlsPerRequest = maxPartUrlsPerRequest; }
    public int getInitialPartUrlCount() { return initialPartUrlCount; }
    public void setInitialPartUrlCount(int initialPartUrlCount) { this.initialPartUrlCount = initialPartUrlCount; }
    public int getUploadUrlTtlSeconds() { return uploadUrlTtlSeconds; }
    public void setUploadUrlTtlSeconds(int uploadUrlTtlSeconds) { this.uploadUrlTtlSeconds = uploadUrlTtlSeconds; }
    public int getMultipartExpireHours() { return multipartExpireHours; }
    public void setMultipartExpireHours(int multipartExpireHours) { this.multipartExpireHours = multipartExpireHours; }
    public int getPlayUrlTtlSeconds() { return playUrlTtlSeconds; }
    public void setPlayUrlTtlSeconds(int playUrlTtlSeconds) { this.playUrlTtlSeconds = playUrlTtlSeconds; }
    public String getPlayTokenSecret() { return playTokenSecret; }
    public void setPlayTokenSecret(String playTokenSecret) { this.playTokenSecret = playTokenSecret; }
    public String getHlsFfmpegPath() { return hlsFfmpegPath; }
    public void setHlsFfmpegPath(String hlsFfmpegPath) { this.hlsFfmpegPath = hlsFfmpegPath; }
    public String getFfprobePath() { return ffprobePath; }
    public void setFfprobePath(String ffprobePath) { this.ffprobePath = ffprobePath; }
    public int getHlsSegmentDurationSeconds() { return hlsSegmentDurationSeconds; }
    public void setHlsSegmentDurationSeconds(int hlsSegmentDurationSeconds) { this.hlsSegmentDurationSeconds = hlsSegmentDurationSeconds; }
    public int getMaxActiveUploadsPerRobot() { return maxActiveUploadsPerRobot; }
    public void setMaxActiveUploadsPerRobot(int maxActiveUploadsPerRobot) { this.maxActiveUploadsPerRobot = maxActiveUploadsPerRobot; }
    public int getMaxActiveUploadsGlobal() { return maxActiveUploadsGlobal; }
    public void setMaxActiveUploadsGlobal(int maxActiveUploadsGlobal) { this.maxActiveUploadsGlobal = maxActiveUploadsGlobal; }
    public int getHlsWorkerConcurrency() { return hlsWorkerConcurrency; }
    public void setHlsWorkerConcurrency(int hlsWorkerConcurrency) { this.hlsWorkerConcurrency = hlsWorkerConcurrency; }
    public int getHlsProcessingLeaseSeconds() { return hlsProcessingLeaseSeconds; }
    public void setHlsProcessingLeaseSeconds(int hlsProcessingLeaseSeconds) { this.hlsProcessingLeaseSeconds = hlsProcessingLeaseSeconds; }
    public int getRetentionDays() { return retentionDays; }
    public void setRetentionDays(int retentionDays) { this.retentionDays = retentionDays; }
    public boolean isTrustedRobotNetworkEnabled() { return trustedRobotNetworkEnabled; }
    public void setTrustedRobotNetworkEnabled(boolean trustedRobotNetworkEnabled) { this.trustedRobotNetworkEnabled = trustedRobotNetworkEnabled; }
    public String getTrustedRobotCidrs() { return trustedRobotCidrs; }
    public void setTrustedRobotCidrs(String trustedRobotCidrs) { this.trustedRobotCidrs = trustedRobotCidrs; }
    public String getDefaultOrgId() { return defaultOrgId; }
    public void setDefaultOrgId(String defaultOrgId) { this.defaultOrgId = defaultOrgId; }
    }
}
