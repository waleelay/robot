package com.robot.control.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "control")
public class ControlProperties {

    private String mediaServiceBaseUrl = "http://localhost:8088";

    public String getMediaServiceBaseUrl() {
        return mediaServiceBaseUrl;
    }

    public void setMediaServiceBaseUrl(String mediaServiceBaseUrl) {
        this.mediaServiceBaseUrl = mediaServiceBaseUrl;
    }
}
