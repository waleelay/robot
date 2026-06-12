package com.robot.bigscreen;

import com.robot.bigscreen.config.CenterServiceProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(CenterServiceProperties.class)
public class BigscreenBffApplication {

    public static void main(String[] args) {
        SpringApplication.run(BigscreenBffApplication.class, args);
    }
}
