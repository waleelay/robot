package com.robot.control.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Control Service 本地运行参数配置。
 *
 * @author leelay
 * @date 2026-07-05
 */
@ConfigurationProperties(prefix = "control")
public class ControlServiceProperties {

    private Auth auth = new Auth();
    private Mqtt mqtt = new Mqtt();
    private Robot robot = new Robot();
    private Session session = new Session();

    /**
     * 返回认证配置。
     *
     * @return 认证配置
     */
    public Auth getAuth() {
        return auth;
    }

    /**
     * 设置认证配置。
     *
     * @param auth 认证配置
     */
    public void setAuth(Auth auth) {
        this.auth = auth;
    }

    /**
     * 返回 MQTT 配置。
     *
     * @return  MQTT 配置
     */
    public Mqtt getMqtt() {
        return mqtt;
    }

    /**
     * 设置 MQTT 配置。
     *
     * @param mqtt MQTT 配置
     */
    public void setMqtt(Mqtt mqtt) {
        this.mqtt = mqtt;
    }

    /**
     * 返回机器人配置。
     *
     * @return 机器人配置
     */
    public Robot getRobot() {
        return robot;
    }

    /**
     * 设置机器人配置。
     *
     * @param robot 机器人配置
     */
    public void setRobot(Robot robot) {
        this.robot = robot;
    }

    /**
     * 返回会话调度配置。
     *
     * @return 会话调度配置
     */
    public Session getSession() {
        return session;
    }

    /**
     * 设置会话调度配置。
     *
     * @param session WebSocket 会话
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
        private String defaultOrgId = "org001";

        /**
         * 返回默认组织 ID。
         *
         * @return 默认组织 ID
         */
        public String getDefaultOrgId() {
            return defaultOrgId;
        }

        /**
         * 设置默认组织 ID。
         *
         * @param defaultOrgId defaultOrgId
         */
        public void setDefaultOrgId(String defaultOrgId) {
            this.defaultOrgId = defaultOrgId;
        }
    }

    /**
     * MQTT 连接与开关配置。
     *
     * @author leelay
     * @date 2026-07-05
     */
    public static class Mqtt {
        private String brokerUrl;
        private String username;
        private String password = "";
        private String clientId = "robot-control-service";
        private boolean enabled = true;

        /**
         * 返回 MQTT Broker 地址。
         *
         * @return  MQTT Broker 地址
         */
        public String getBrokerUrl() {
            return brokerUrl;
        }

        /**
         * 设置 MQTT Broker 地址。
         *
         * @param brokerUrl Broker 地址
         */
        public void setBrokerUrl(String brokerUrl) {
            this.brokerUrl = brokerUrl;
        }

        /**
         * 返回用户名。
         *
         * @return 用户名
         */
        public String getUsername() {
            return username;
        }

        /**
         * 设置用户名。
         *
         * @param username 用户名
         */
        public void setUsername(String username) {
            this.username = username;
        }

        /**
         * 返回密码。
         *
         * @return 密码
         */
        public String getPassword() {
            return password;
        }

        /**
         * 设置密码。
         *
         * @param password 密码
         */
        public void setPassword(String password) {
            this.password = password;
        }

        /**
         * 返回 MQTT Client ID。
         *
         * @return  MQTT Client ID
         */
        public String getClientId() {
            return clientId;
        }

        /**
         * 设置 MQTT Client ID。
         *
         * @param clientId 客户端 ID
         */
        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        /**
         * 返回是否启用。
         *
         * @return 是否启用
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 设置是否启用。
         *
         * @param enabled 是否启用
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
        private long heartbeatTimeoutSeconds = 15;

        /**
         * 返回机器人心跳超时时间。
         *
         * @return 机器人心跳超时时间
         */
        public long getHeartbeatTimeoutSeconds() {
            return heartbeatTimeoutSeconds;
        }

        /**
         * 设置机器人心跳超时时间。
         *
         * @param heartbeatTimeoutSeconds 心跳超时时间
         */
        public void setHeartbeatTimeoutSeconds(long heartbeatTimeoutSeconds) {
            this.heartbeatTimeoutSeconds = heartbeatTimeoutSeconds;
        }
    }

    /**
     * 视频会话后台任务配置。
     *
     * @author leelay
     * @date 2026-07-05
     */
    public static class Session {
        private long interruptedGraceSeconds = 15;
        private long idleReleaseDelaySeconds = 600;
        private long viewerHeartbeatTimeoutSeconds = 15;

        /**
         * 返回中断恢复宽限时间。
         *
         * @return 中断恢复宽限时间
         */
        public long getInterruptedGraceSeconds() {
            return interruptedGraceSeconds;
        }

        /**
         * 设置中断恢复宽限时间。
         *
         * @param interruptedGraceSeconds 中断恢复宽限时间
         */
        public void setInterruptedGraceSeconds(long interruptedGraceSeconds) {
            this.interruptedGraceSeconds = interruptedGraceSeconds;
        }

        /**
         * 返回空闲释放延迟时间。
         *
         * @return 空闲释放延迟时间
         */
        public long getIdleReleaseDelaySeconds() {
            return idleReleaseDelaySeconds;
        }

        /**
         * 设置空闲释放延迟时间。
         *
         * @param idleReleaseDelaySeconds 空闲释放延迟时间
         */
        public void setIdleReleaseDelaySeconds(long idleReleaseDelaySeconds) {
            this.idleReleaseDelaySeconds = idleReleaseDelaySeconds;
        }

        /**
         * 返回观看者心跳超时时间。
         *
         * @return 观看者心跳超时时间
         */
        public long getViewerHeartbeatTimeoutSeconds() {
            return viewerHeartbeatTimeoutSeconds;
        }

        /**
         * 设置观看者心跳超时时间。
         *
         * @param viewerHeartbeatTimeoutSeconds 观看者心跳超时时间
         */
        public void setViewerHeartbeatTimeoutSeconds(long viewerHeartbeatTimeoutSeconds) {
            this.viewerHeartbeatTimeoutSeconds = viewerHeartbeatTimeoutSeconds;
        }
    }
}
