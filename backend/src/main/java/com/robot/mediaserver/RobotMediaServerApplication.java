package com.robot.mediaserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 媒体服务启动入口。
 *
 * @author leelay
 * @date 2026/05/19
 */
@SpringBootApplication
public class RobotMediaServerApplication {

    /**
     * 启动媒体服务应用。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(RobotMediaServerApplication.class, args);
    }
}
