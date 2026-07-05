package com.robot.control.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Control Service 对外部 Media Service 的连接配置。
 *
 * @author leelay
 * @date 2026-07-05
 */
@ConfigurationProperties(prefix = "control")
public class ControlProperties {

    private String mediaServiceBaseUrl = "http://localhost:8088";

    /**
     * 返回 Media Service 基础地址。
     *
     * @return Media Service 基础地址
     */
    public String getMediaServiceBaseUrl() {
        return mediaServiceBaseUrl;
    }

    /**
     * 设置 Media Service 基础地址。
     *
     * @param mediaServiceBaseUrl Media Service 基础地址
     */
    public void setMediaServiceBaseUrl(String mediaServiceBaseUrl) {
        this.mediaServiceBaseUrl = mediaServiceBaseUrl;
    }
}
