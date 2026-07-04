package com.robot.control.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "control")
public class ControlServiceProperties {

    private Auth auth = new Auth();
    private Mqtt mqtt = new Mqtt();
    private Robot robot = new Robot();
    private Session session = new Session();

    public Auth getAuth() {
        return auth;
    }

    public void setAuth(Auth auth) {
        this.auth = auth;
    }

    public Mqtt getMqtt() {
        return mqtt;
    }

    public void setMqtt(Mqtt mqtt) {
        this.mqtt = mqtt;
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

    public static class Auth {
        private String defaultOrgId = "org001";

        public String getDefaultOrgId() {
            return defaultOrgId;
        }

        public void setDefaultOrgId(String defaultOrgId) {
            this.defaultOrgId = defaultOrgId;
        }
    }

    public static class Mqtt {
        private String brokerUrl;
        private String username;
        private String password = "";
        private String clientId = "robot-control-service";
        private boolean enabled = true;

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

    public static class Robot {
        private long heartbeatTimeoutSeconds = 15;

        public long getHeartbeatTimeoutSeconds() {
            return heartbeatTimeoutSeconds;
        }

        public void setHeartbeatTimeoutSeconds(long heartbeatTimeoutSeconds) {
            this.heartbeatTimeoutSeconds = heartbeatTimeoutSeconds;
        }
    }

    public static class Session {
        private long interruptedGraceSeconds = 15;
        private long idleReleaseDelaySeconds = 600;
        private long viewerHeartbeatTimeoutSeconds = 15;

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
    }
}
