package com.robot.bigscreen.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "center")
public class CenterServiceProperties {

    private String manageBaseUrl = "http://localhost:8088";
    private String controlBaseUrl = "http://localhost:8088";
    private String v1ControlBaseUrl = "http://localhost:8088";
    private String mediaBaseUrl = "http://localhost:8088";
    private String websocketControlUrl = "ws://localhost:8088/ws/control";

    public String getManageBaseUrl() {
        return manageBaseUrl;
    }

    public void setManageBaseUrl(String manageBaseUrl) {
        this.manageBaseUrl = manageBaseUrl;
    }

    public String getControlBaseUrl() {
        return controlBaseUrl;
    }

    public void setControlBaseUrl(String controlBaseUrl) {
        this.controlBaseUrl = controlBaseUrl;
    }

    public String getV1ControlBaseUrl() {
        return v1ControlBaseUrl;
    }

    public void setV1ControlBaseUrl(String v1ControlBaseUrl) {
        this.v1ControlBaseUrl = v1ControlBaseUrl;
    }

    public String getMediaBaseUrl() {
        return mediaBaseUrl;
    }

    public void setMediaBaseUrl(String mediaBaseUrl) {
        this.mediaBaseUrl = mediaBaseUrl;
    }

    public String getWebsocketControlUrl() {
        return websocketControlUrl;
    }

    public void setWebsocketControlUrl(String websocketControlUrl) {
        this.websocketControlUrl = websocketControlUrl;
    }
}
