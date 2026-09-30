package com.robot.control.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 控制服务的下游连接、认证、消息通信和运行参数配置。
 *
 * @author leelay
 * @date 2026-07-05
 */
@ConfigurationProperties(prefix = "control")
public class ControlServiceProperties {

    /**
     * Media 服务 HTTP 基础地址。
     */
    private String mediaServiceBaseUrl = "http://localhost:8088";
    /**
     * Management 服务 HTTP 基础地址。
     */
    private String managementServiceBaseUrl = "http://host.docker.internal:8866";
    /**
     * 设备档案及授权列表的缓存有效秒数；Management 客户端对非正值回退到 30 秒，正值最多取 30 秒。
     */
    private int deviceCacheTtlSeconds = 30;

    /**
     * 认证配置。
     */
    private Auth auth = new Auth();
    /**
     * MQTT 配置。
     */
    private Mqtt mqtt = new Mqtt();
    /**
     * 机器人配置。
     */
    private Robot robot = new Robot();
    /**
     * 控制侧视频恢复、空闲释放及对讲超时配置
     */
    private Session session = new Session();
    /**
     * 管理端 STOMP 连接及重连配置。
     */
    private CenterStomp centerStomp = new CenterStomp();

    /**
     * 读取{@link #mediaServiceBaseUrl}。
     *
     * @return 当前值，含义与约束见{@link #mediaServiceBaseUrl}
     */
    public String getMediaServiceBaseUrl() {
        return mediaServiceBaseUrl;
    }

    /**
     * 更新{@link #mediaServiceBaseUrl}。
     *
     * @param mediaServiceBaseUrl 新值，含义与约束见{@link #mediaServiceBaseUrl}
     */
    public void setMediaServiceBaseUrl(String mediaServiceBaseUrl) {
        this.mediaServiceBaseUrl = mediaServiceBaseUrl;
    }

    /**
     * 读取{@link #managementServiceBaseUrl}。
     *
     * @return 当前值，含义与约束见{@link #managementServiceBaseUrl}
     */
    public String getManagementServiceBaseUrl() {
        return managementServiceBaseUrl;
    }

    /**
     * 更新{@link #managementServiceBaseUrl}。
     *
     * @param managementServiceBaseUrl 新值，含义与约束见{@link #managementServiceBaseUrl}
     */
    public void setManagementServiceBaseUrl(String managementServiceBaseUrl) {
        this.managementServiceBaseUrl = managementServiceBaseUrl;
    }

    /**
     * 读取{@link #deviceCacheTtlSeconds}。
     *
     * @return 当前值，含义与约束见{@link #deviceCacheTtlSeconds}
     */
    public int getDeviceCacheTtlSeconds() {
        return deviceCacheTtlSeconds;
    }

    /**
     * 更新{@link #deviceCacheTtlSeconds}。
     *
     * @param deviceCacheTtlSeconds 新值，含义与约束见{@link #deviceCacheTtlSeconds}
     */
    public void setDeviceCacheTtlSeconds(int deviceCacheTtlSeconds) {
        this.deviceCacheTtlSeconds = deviceCacheTtlSeconds;
    }

    /**
     * 读取{@link #auth}。
     *
     * @return 当前值，含义与约束见{@link #auth}
     */
    public Auth getAuth() {
        return auth;
    }

    /**
     * 更新{@link #auth}。
     *
     * @param auth 新值，含义与约束见{@link #auth}
     */
    public void setAuth(Auth auth) {
        this.auth = auth;
    }

    /**
     * 读取{@link #mqtt}。
     *
     * @return 当前值，含义与约束见{@link #mqtt}
     */
    public Mqtt getMqtt() {
        return mqtt;
    }

    /**
     * 更新{@link #mqtt}。
     *
     * @param mqtt 新值，含义与约束见{@link #mqtt}
     */
    public void setMqtt(Mqtt mqtt) {
        this.mqtt = mqtt;
    }

    /**
     * 读取{@link #centerStomp}。
     *
     * @return 当前值，含义与约束见{@link #centerStomp}
     */
    public CenterStomp getCenterStomp() {
        return centerStomp;
    }

    /**
     * 更新{@link #centerStomp}。
     *
     * @param centerStomp 新值，含义与约束见{@link #centerStomp}
     */
    public void setCenterStomp(CenterStomp centerStomp) {
        this.centerStomp = centerStomp;
    }

    /**
     * 读取{@link #robot}。
     *
     * @return 当前值，含义与约束见{@link #robot}
     */
    public Robot getRobot() {
        return robot;
    }

    /**
     * 更新{@link #robot}。
     *
     * @param robot 新值，含义与约束见{@link #robot}
     */
    public void setRobot(Robot robot) {
        this.robot = robot;
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
     * 认证与默认组织配置。
     *
     * @author leelay
     * @date 2026-07-05
     */
    public static class Auth {
        /**
         * 默认组织 ID。
         */
        private String defaultOrgId = "org001";
        /**
         * true 表示允许开发默认用户。
         */
        private boolean allowDefaultUser = false;

        /**
         * 读取{@link #defaultOrgId}。
         *
         * @return 当前值，含义与约束见{@link #defaultOrgId}
         */
        public String getDefaultOrgId() {
            return defaultOrgId;
        }

        /**
         * 更新{@link #defaultOrgId}。
         *
         * @param defaultOrgId 新值，含义与约束见{@link #defaultOrgId}
         */
        public void setDefaultOrgId(String defaultOrgId) {
            this.defaultOrgId = defaultOrgId;
        }

        /**
         * 读取{@link #allowDefaultUser}。
         *
         * @return 当前值，含义与约束见{@link #allowDefaultUser}
         */
        public boolean isAllowDefaultUser() {
            return allowDefaultUser;
        }

        /**
         * 更新{@link #allowDefaultUser}。
         *
         * @param allowDefaultUser 新值，含义与约束见{@link #allowDefaultUser}
         */
        public void setAllowDefaultUser(boolean allowDefaultUser) {
            this.allowDefaultUser = allowDefaultUser;
        }
    }

    /**
     * MQTT 连接与开关配置。
     *
     * @author leelay
     * @date 2026-07-05
     */
    public static class Mqtt {
        /**
         * MQTT Broker 地址。
         */
        private String brokerUrl;
        /**
         * 用户名。
         */
        private String username;
        /**
         * 密码。
         */
        private String password = "";
        /**
         * MQTT 客户端标识。
         */
        private String clientId = "robot-control-service";
        /**
         * 固定摄像头所属 Gateway 标识。
         */
        private String fixedCameraGatewayId = "default";
        /**
         * 是否启用。
         */
        private boolean enabled = true;

        /**
         * 读取{@link #brokerUrl}。
         *
         * @return 当前值，含义与约束见{@link #brokerUrl}
         */
        public String getBrokerUrl() {
            return brokerUrl;
        }

        /**
         * 更新{@link #brokerUrl}。
         *
         * @param brokerUrl 新值，含义与约束见{@link #brokerUrl}
         */
        public void setBrokerUrl(String brokerUrl) {
            this.brokerUrl = brokerUrl;
        }

        /**
         * 读取{@link #username}。
         *
         * @return 当前值，含义与约束见{@link #username}
         */
        public String getUsername() {
            return username;
        }

        /**
         * 更新{@link #username}。
         *
         * @param username 新值，含义与约束见{@link #username}
         */
        public void setUsername(String username) {
            this.username = username;
        }

        /**
         * 读取{@link #password}。
         *
         * @return 当前值，含义与约束见{@link #password}
         */
        public String getPassword() {
            return password;
        }

        /**
         * 更新{@link #password}。
         *
         * @param password 新值，含义与约束见{@link #password}
         */
        public void setPassword(String password) {
            this.password = password;
        }

        /**
         * 读取{@link #clientId}。
         *
         * @return 当前值，含义与约束见{@link #clientId}
         */
        public String getClientId() {
            return clientId;
        }

        /**
         * 更新{@link #clientId}。
         *
         * @param clientId 新值，含义与约束见{@link #clientId}
         */
        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        /**
         * 读取{@link #fixedCameraGatewayId}。
         *
         * @return 当前值，含义与约束见{@link #fixedCameraGatewayId}
         */
        public String getFixedCameraGatewayId() {
            return fixedCameraGatewayId;
        }

        /**
         * 更新{@link #fixedCameraGatewayId}。
         *
         * @param fixedCameraGatewayId 新值，含义与约束见{@link #fixedCameraGatewayId}
         */
        public void setFixedCameraGatewayId(String fixedCameraGatewayId) {
            this.fixedCameraGatewayId = fixedCameraGatewayId;
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

    /**
     * 机器人在线状态配置。
     *
     * @author leelay
     * @date 2026-07-05
     */
    public static class Robot {
        /**
         * 机器人心跳超时时间。
         */
        private long heartbeatTimeoutSeconds = 30;
        /**
         * 离线注册表条目保留时间。
         */
        private long offlineRetentionSeconds = 60;

        /**
         * 读取{@link #heartbeatTimeoutSeconds}。
         *
         * @return 当前值，含义与约束见{@link #heartbeatTimeoutSeconds}
         */
        public long getHeartbeatTimeoutSeconds() {
            return heartbeatTimeoutSeconds;
        }

        /**
         * 更新{@link #heartbeatTimeoutSeconds}。
         *
         * @param heartbeatTimeoutSeconds 新值，含义与约束见{@link #heartbeatTimeoutSeconds}
         */
        public void setHeartbeatTimeoutSeconds(long heartbeatTimeoutSeconds) {
            this.heartbeatTimeoutSeconds = heartbeatTimeoutSeconds;
        }

        /**
         * 读取{@link #offlineRetentionSeconds}。
         *
         * @return 当前值，含义与约束见{@link #offlineRetentionSeconds}
         */
        public long getOfflineRetentionSeconds() {
            return offlineRetentionSeconds;
        }

        /**
         * 更新{@link #offlineRetentionSeconds}。
         *
         * @param offlineRetentionSeconds 新值，含义与约束见{@link #offlineRetentionSeconds}
         */
        public void setOfflineRetentionSeconds(long offlineRetentionSeconds) {
            this.offlineRetentionSeconds = offlineRetentionSeconds;
        }
    }

    /** 管理端 STOMP 上游连接、认证、订阅和重连配置。 */
    public static class CenterStomp {
        /**
         * 是否启用管理端 STOMP 事件桥接
         */
        private boolean enabled = true;
        /**
         * 上游 WebSocket 连接地址。
         */
        private String websocketUrl = "ws://localhost:8867/ws/control";
        /**
         * 管理端 STOMP 实时事件订阅目的地
         */
        private String topic = "/topic/platform/realtime-events";
        /**
         * 上游访问令牌，不得写入日志。
         */
        private String accessToken;
        /**
         * OAuth 访问令牌申请地址。
         */
        private String tokenUrl;
        /**
         * 客户端 ID。
         */
        private String clientId;
        /**
         * OAuth 客户端密钥，不得写入日志。
         */
        private String clientSecret;
        /**
         * 连接中断后的重连间隔，单位毫秒。
         */
        private long reconnectDelayMs = 5000;

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
         * 读取{@link #websocketUrl}。
         *
         * @return 当前值，含义与约束见{@link #websocketUrl}
         */
        public String getWebsocketUrl() { return websocketUrl; }
        /**
         * 更新{@link #websocketUrl}。
         *
         * @param websocketUrl 新值，含义与约束见{@link #websocketUrl}
         */
        public void setWebsocketUrl(String websocketUrl) { this.websocketUrl = websocketUrl; }
        /**
         * 读取{@link #topic}。
         *
         * @return 当前值，含义与约束见{@link #topic}
         */
        public String getTopic() { return topic; }
        /**
         * 更新{@link #topic}。
         *
         * @param topic 新值，含义与约束见{@link #topic}
         */
        public void setTopic(String topic) { this.topic = topic; }
        /**
         * 读取{@link #accessToken}。
         *
         * @return 当前值，含义与约束见{@link #accessToken}
         */
        public String getAccessToken() { return accessToken; }
        /**
         * 更新{@link #accessToken}。
         *
         * @param accessToken 新值，含义与约束见{@link #accessToken}
         */
        public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
        /**
         * 读取{@link #tokenUrl}。
         *
         * @return 当前值，含义与约束见{@link #tokenUrl}
         */
        public String getTokenUrl() { return tokenUrl; }
        /**
         * 更新{@link #tokenUrl}。
         *
         * @param tokenUrl 新值，含义与约束见{@link #tokenUrl}
         */
        public void setTokenUrl(String tokenUrl) { this.tokenUrl = tokenUrl; }
        /**
         * 读取{@link #clientId}。
         *
         * @return 当前值，含义与约束见{@link #clientId}
         */
        public String getClientId() { return clientId; }
        /**
         * 更新{@link #clientId}。
         *
         * @param clientId 新值，含义与约束见{@link #clientId}
         */
        public void setClientId(String clientId) { this.clientId = clientId; }
        /**
         * 读取{@link #clientSecret}。
         *
         * @return 当前值，含义与约束见{@link #clientSecret}
         */
        public String getClientSecret() { return clientSecret; }
        /**
         * 更新{@link #clientSecret}。
         *
         * @param clientSecret 新值，含义与约束见{@link #clientSecret}
         */
        public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
        /**
         * 读取{@link #reconnectDelayMs}。
         *
         * @return 当前值，含义与约束见{@link #reconnectDelayMs}
         */
        public long getReconnectDelayMs() { return reconnectDelayMs; }
        /**
         * 更新{@link #reconnectDelayMs}。
         *
         * @param reconnectDelayMs 新值，含义与约束见{@link #reconnectDelayMs}
         */
        public void setReconnectDelayMs(long reconnectDelayMs) { this.reconnectDelayMs = reconnectDelayMs; }
    }

    /**
     * 视频会话后台任务配置。
     *
     * @author leelay
     * @date 2026-07-05
     */
    public static class Session {
        /**
         * 中断恢复宽限时间。
         */
        private long interruptedGraceSeconds = 15;
        /**
         * 空闲释放延迟时间。
         */
        private long idleReleaseDelaySeconds = 30;
        /**
         * 观看者心跳超时时间。
         */
        private long viewerHeartbeatTimeoutSeconds = 15;

        /**
         * 读取{@link #interruptedGraceSeconds}。
         *
         * @return 当前值，含义与约束见{@link #interruptedGraceSeconds}
         */
        public long getInterruptedGraceSeconds() {
            return interruptedGraceSeconds;
        }

        /**
         * 更新{@link #interruptedGraceSeconds}。
         *
         * @param interruptedGraceSeconds 新值，含义与约束见{@link #interruptedGraceSeconds}
         */
        public void setInterruptedGraceSeconds(long interruptedGraceSeconds) {
            this.interruptedGraceSeconds = interruptedGraceSeconds;
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
    }
}
