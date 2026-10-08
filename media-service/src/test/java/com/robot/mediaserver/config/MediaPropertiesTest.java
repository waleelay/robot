package com.robot.mediaserver.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;

/** 验证 HLS 配置迁移保留旧值、新名称优先，并兼容独立外部配置文件。 */
class MediaPropertiesTest {

    @ParameterizedTest
    @MethodSource("propertyCases")
    void bindsTimeoutKeysWithAndWithoutBundledYaml(boolean loadYaml, int expected, String[] values) {
        runner(loadYaml, Map.of()).withPropertyValues(values).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(MediaProperties.class);
            assertThat(context.getBean(MediaProperties.class).getFile().getHlsProcessingTimeoutSeconds())
                    .isEqualTo(expected);
        });
    }

    private static Stream<Arguments> propertyCases() {
        return Stream.of(false, true).flatMap(loadYaml -> Stream.of(
                Arguments.of(loadYaml, 300, new String[] {}),
                Arguments.of(loadYaml, 420, new String[] {"media.file.hls-processing-lease-seconds=420"}),
                Arguments.of(loadYaml, 510, new String[] {"media.file.hls-processing-timeout-seconds=510"}),
                Arguments.of(loadYaml, 510, new String[] {
                        "media.file.hls-processing-lease-seconds=420",
                        "media.file.hls-processing-timeout-seconds=510"}),
                Arguments.of(loadYaml, 300, new String[] {"media.file.hls-processing-lease-seconds="}),
                Arguments.of(loadYaml, 300, new String[] {"media.file.hls-processing-timeout-seconds="}),
                Arguments.of(loadYaml, 300, new String[] {
                        "media.file.hls-processing-lease-seconds=", "media.file.hls-processing-timeout-seconds="}),
                Arguments.of(loadYaml, 420, new String[] {
                        "media.file.hls-processing-lease-seconds=420", "media.file.hls-processing-timeout-seconds="}),
                Arguments.of(loadYaml, 510, new String[] {
                        "media.file.hls-processing-lease-seconds=", "media.file.hls-processing-timeout-seconds=510"})));
    }

    @ParameterizedTest
    @MethodSource("environmentCases")
    void resolvesEnvironmentAliasesFromBundledYaml(Map<String, Object> environment, int expected, String[] values) {
        runner(true, environment).withPropertyValues(values).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(MediaProperties.class).getFile().getHlsProcessingTimeoutSeconds())
                    .isEqualTo(expected);
        });
    }

    private static Stream<Arguments> environmentCases() {
        return Stream.of(
                Arguments.of(Map.of("MEDIA_FILE_HLS_PROCESSING_LEASE_SECONDS", "420"), 420, new String[] {}),
                Arguments.of(Map.of("MEDIA_FILE_HLS_PROCESSING_TIMEOUT_SECONDS", "510"), 510, new String[] {}),
                Arguments.of(Map.of("MEDIA_FILE_HLS_PROCESSING_LEASE_SECONDS", "420",
                        "MEDIA_FILE_HLS_PROCESSING_TIMEOUT_SECONDS", "510"), 510, new String[] {}),
                Arguments.of(Map.of("MEDIA_FILE_HLS_PROCESSING_LEASE_SECONDS", ""), 300, new String[] {}),
                Arguments.of(Map.of("MEDIA_FILE_HLS_PROCESSING_TIMEOUT_SECONDS", ""), 300, new String[] {}),
                Arguments.of(Map.of("MEDIA_FILE_HLS_PROCESSING_LEASE_SECONDS", "",
                        "MEDIA_FILE_HLS_PROCESSING_TIMEOUT_SECONDS", ""), 300, new String[] {}),
                Arguments.of(Map.of("MEDIA_FILE_HLS_PROCESSING_LEASE_SECONDS", "420",
                        "MEDIA_FILE_HLS_PROCESSING_TIMEOUT_SECONDS", ""), 420, new String[] {}),
                Arguments.of(Map.of("MEDIA_FILE_HLS_PROCESSING_LEASE_SECONDS", "",
                        "MEDIA_FILE_HLS_PROCESSING_TIMEOUT_SECONDS", "510"), 510, new String[] {}),
                Arguments.of(Map.of("MEDIA_FILE_HLS_PROCESSING_TIMEOUT_SECONDS", ""), 420,
                        new String[] {"media.file.hls-processing-lease-seconds=420"}),
                Arguments.of(Map.of("MEDIA_FILE_HLS_PROCESSING_TIMEOUT_SECONDS", "510"), 510,
                        new String[] {"media.file.hls-processing-lease-seconds=420"}));
    }

    private ApplicationContextRunner runner(boolean loadYaml, Map<String, Object> environment) {
        return new ApplicationContextRunner().withUserConfiguration(BindingConfiguration.class)
                .withInitializer(context -> {
                    var sources = context.getEnvironment().getPropertySources();
                    sources.replace("systemEnvironment",
                            new SystemEnvironmentPropertySource("systemEnvironment", environment));
                    if (loadYaml) {
                        try {
                            new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))
                                    .forEach(sources::addLast);
                        } catch (IOException ex) {
                            throw new UncheckedIOException(ex);
                        }
                    }
                });
    }

    /** 仅装配真实配置绑定，不启动数据库、存储或媒体服务。 */
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(MediaProperties.class)
    static class BindingConfiguration {
    }
}
