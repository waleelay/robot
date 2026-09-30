package com.robot.bigscreen.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 配置 Management、Control、Media 及现场呼叫的下游 HTTP/WebSocket 地址。 */
@ConfigurationProperties(prefix = "center")
public class DownstreamServiceProperties {

    /**
     * Management 服务 HTTP 基址。
     */
    private String manageBaseUrl = "http://localhost:8088";
    /**
     * Control 服务的内部 HTTP 基址。
     */
    private String controlBaseUrl = "http://localhost:8088";
    /**
     * 管理端装备控制接口基址。
     */
    private String eiopControlBaseUrl = "http://localhost:8088";
    /**
     * Media 服务内部 HTTP 基址。
     */
    private String mediaBaseUrl = "http://localhost:8088";
    /**
     * Control WebSocket 上游地址。
     */
    private String websocketControlUrl = "ws://localhost:8088/ws/control";
    /**
     * 现场呼叫 WebSocket 上游地址。
     */
    private String websocketFieldCallUrl = "ws://localhost:8082/ws/field-call";

    /**
     * 读取{@link #manageBaseUrl}。
     *
     * @return 当前值，含义与约束见{@link #manageBaseUrl}
     */
    public String getManageBaseUrl() {
        return manageBaseUrl;
    }

    /**
     * 更新{@link #manageBaseUrl}。
     *
     * @param manageBaseUrl 新值，含义与约束见{@link #manageBaseUrl}
     */
    public void setManageBaseUrl(String manageBaseUrl) {
        this.manageBaseUrl = manageBaseUrl;
    }

    /**
     * 读取{@link #controlBaseUrl}。
     *
     * @return 当前值，含义与约束见{@link #controlBaseUrl}
     */
    public String getControlBaseUrl() {
        return controlBaseUrl;
    }

    /**
     * 更新{@link #controlBaseUrl}。
     *
     * @param controlBaseUrl 新值，含义与约束见{@link #controlBaseUrl}
     */
    public void setControlBaseUrl(String controlBaseUrl) {
        this.controlBaseUrl = controlBaseUrl;
    }

    /**
     * 读取{@link #eiopControlBaseUrl}。
     *
     * @return 当前值，含义与约束见{@link #eiopControlBaseUrl}
     */
    public String getEiopControlBaseUrl() {
        return eiopControlBaseUrl;
    }

    /**
     * 更新{@link #eiopControlBaseUrl}。
     *
     * @param eiopControlBaseUrl 新值，含义与约束见{@link #eiopControlBaseUrl}
     */
    public void setEiopControlBaseUrl(String eiopControlBaseUrl) {
        this.eiopControlBaseUrl = eiopControlBaseUrl;
    }

    /**
     * 读取{@link #mediaBaseUrl}。
     *
     * @return 当前值，含义与约束见{@link #mediaBaseUrl}
     */
    public String getMediaBaseUrl() {
        return mediaBaseUrl;
    }

    /**
     * 更新{@link #mediaBaseUrl}。
     *
     * @param mediaBaseUrl 新值，含义与约束见{@link #mediaBaseUrl}
     */
    public void setMediaBaseUrl(String mediaBaseUrl) {
        this.mediaBaseUrl = mediaBaseUrl;
    }

    /**
     * 读取{@link #websocketControlUrl}。
     *
     * @return 当前值，含义与约束见{@link #websocketControlUrl}
     */
    public String getWebsocketControlUrl() {
        return websocketControlUrl;
    }

    /**
     * 更新{@link #websocketControlUrl}。
     *
     * @param websocketControlUrl 新值，含义与约束见{@link #websocketControlUrl}
     */
    public void setWebsocketControlUrl(String websocketControlUrl) {
        this.websocketControlUrl = websocketControlUrl;
    }

    /**
     * 读取{@link #websocketFieldCallUrl}。
     *
     * @return 当前值，含义与约束见{@link #websocketFieldCallUrl}
     */
    public String getWebsocketFieldCallUrl() {
        return websocketFieldCallUrl;
    }

    /**
     * 更新{@link #websocketFieldCallUrl}。
     *
     * @param websocketFieldCallUrl 新值，含义与约束见{@link #websocketFieldCallUrl}
     */
    public void setWebsocketFieldCallUrl(String websocketFieldCallUrl) {
        this.websocketFieldCallUrl = websocketFieldCallUrl;
    }
}
