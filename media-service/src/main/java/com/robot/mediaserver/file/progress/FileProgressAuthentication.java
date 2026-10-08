package com.robot.mediaserver.file.progress;

import com.robot.mediaserver.config.MediaProperties;
import com.robot.mediaserver.file.api.FileApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** 校验 MinIO 文件进度回调的内部 Bearer 凭证；未配置凭证时拒绝服务。 */
@Component
public class FileProgressAuthentication {

    private static final String BEARER_PREFIX = "Bearer ";

    private final MediaProperties properties;

    /**
     * 初始化 FileProgressAuthentication，保存所需依赖及初始运行状态。
     *
     * @param properties 服务配置
     */
    public FileProgressAuthentication(MediaProperties properties) {
        this.properties = properties;
    }

    /**
     * 验证 MinIO 回调凭证，拒绝缺失或不匹配的内部令牌。
     *
     * @param authorization 调用方提供的 Authorization 头；凭据不得写入日志
     */
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
