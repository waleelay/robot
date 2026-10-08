package com.robot.mediaserver;

import com.robot.mediaserver.config.MediaProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 启动媒体服务，装配视频、文件和语音合成相关组件。 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(MediaProperties.class)
public class RobotMediaServerApplication {

    /**
     * 启动本模块的 Spring Boot 应用，使用外部配置装配服务。
     *
     * @param args 命令行启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(RobotMediaServerApplication.class, args);
    }
}
