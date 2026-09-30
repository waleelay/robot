package com.robot.bigscreen;

import com.robot.bigscreen.config.DownstreamServiceProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/** 启动大屏 BFF，装配认证、业务聚合和实时事件桥接。 */
@SpringBootApplication
@EnableConfigurationProperties(DownstreamServiceProperties.class)
public class BigscreenBffApplication {

    /**
     * 启动本模块的 Spring Boot 应用，使用外部配置装配服务。
     *
     * @param args 命令行启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(BigscreenBffApplication.class, args);
    }
}
