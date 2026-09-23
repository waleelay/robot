package com.robot.mediaserver.file.progress;

import com.robot.mediaserver.config.MediaProperties;
import com.robot.mediaserver.file.api.FileApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class FileProgressAuthentication {

    private static final String BEARER_PREFIX = "Bearer ";

    private final MediaProperties properties;

    public FileProgressAuthentication(MediaProperties properties) {
        this.properties = properties;
    }

    public void requireWebhookToken(String authorization) {
        requireToken(authorization, properties.getFile().getProgressWebhookToken());
    }

    private void requireToken(String authorization, String expected) {
        if (expected == null || expected.isBlank()) {
            throw new FileApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "INTERNAL_TOKEN_NOT_CONFIGURED",
                    "文件进度内部接口凭证未配置");
        }
        String actual = authorization != null && authorization.startsWith(BEARER_PREFIX)
                ? authorization.substring(BEARER_PREFIX.length())
                : "";
        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8))) {
            throw new FileApiException(
                    HttpStatus.UNAUTHORIZED,
                    "INTERNAL_TOKEN_INVALID",
                    "文件进度内部接口凭证无效");
        }
    }
}
