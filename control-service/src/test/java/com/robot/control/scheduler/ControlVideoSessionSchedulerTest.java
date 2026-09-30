package com.robot.control.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.robot.control.client.ControlMediaServiceClient;
import com.robot.control.config.ControlServiceProperties;
import com.robot.control.service.ControlVideoCommandService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.FixedDelayTask;

/** 验证真实调度注册使用 Control 新配置，并保留旧配置和环境变量的回退。 */
class ControlVideoSessionSchedulerTest {

    @ParameterizedTest
    @MethodSource("propertyCases")
    void registersDelayFromConfiguration(boolean loadYaml, int expected, String[] values) {
        verifyDelay(loadYaml, Map.of(), expected, values);
    }

    private static Stream<Arguments> propertyCases() {
        return Stream.of(false, true).flatMap(loadYaml -> Stream.of(
                Arguments.of(loadYaml, 5000, new String[] {}),
                Arguments.of(loadYaml, 12000, new String[] {"media.session.sweep-delay-ms=12000"}),
                Arguments.of(loadYaml, 9000, new String[] {"control.session.sweep-delay-ms=9000"}),
                Arguments.of(loadYaml, 9000, new String[] {
                        "media.session.sweep-delay-ms=12000", "control.session.sweep-delay-ms=9000"})));
    }

    @ParameterizedTest
    @MethodSource("environmentCases")
    void registersDelayFromEnvironment(Map<String, Object> environment, int expected) {
        verifyDelay(true, environment, expected);
    }

    private static Stream<Arguments> environmentCases() {
        return Stream.of(
                Arguments.of(Map.of("MEDIA_SESSION_SWEEP_DELAY_MS", "12000"), 12000),
                Arguments.of(Map.of("CONTROL_SESSION_SWEEP_DELAY_MS", "9000"), 9000),
                Arguments.of(Map.of("MEDIA_SESSION_SWEEP_DELAY_MS", "12000",
                        "CONTROL_SESSION_SWEEP_DELAY_MS", "9000"), 9000));
    }

    private void verifyDelay(boolean loadYaml, Map<String, Object> environment, int expected, String... values) {
        ControlMediaServiceClient media = mock(ControlMediaServiceClient.class);
        ControlVideoCommandService commands = mock(ControlVideoCommandService.class);
        new ApplicationContextRunner().withUserConfiguration(SchedulingConfiguration.class)
                // 使用调度替身检查实际注册参数，不启动后台扫描或调用下游。
                .withBean(TaskScheduler.class, () -> mock(TaskScheduler.class))
                .withBean(ControlMediaServiceClient.class, () -> media)
                .withBean(ControlVideoCommandService.class, () -> commands)
                .withPropertyValues(values)
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
                }).run(context -> {
                    assertThat(context).hasNotFailed();
                    var tasks = context.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks();
                    assertThat(tasks).singleElement().satisfies(task -> {
                        assertThat(task.getTask()).isInstanceOf(FixedDelayTask.class);
                        assertThat(((FixedDelayTask) task.getTask()).getIntervalDuration())
                                .isEqualTo(Duration.ofMillis(expected));
                    });
                    verifyNoInteractions(media, commands);
                });
    }

    /** 装配真实调度器及参数绑定，仅由测试替换外部客户端与计时执行器。 */
    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @EnableConfigurationProperties(ControlServiceProperties.class)
    @Import(ControlVideoSessionScheduler.class)
    static class SchedulingConfiguration {
    }
}
