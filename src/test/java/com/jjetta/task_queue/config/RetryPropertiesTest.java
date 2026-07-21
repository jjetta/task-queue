package com.jjetta.task_queue.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

public class RetryPropertiesTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(RetryProperties.class)
    static class TestConfig {}

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void shouldBindPropertiesCorrectly() {
        this.contextRunner
                .withPropertyValues(
                        "app.retry.max-retries=3",
                        "app.retry.base-delay=1s",
                        "app.retry.max-delay=30s",
                        "app.retry.jitter=2s"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(RetryProperties.class);

                    RetryProperties properties = context.getBean(RetryProperties.class);
                    assertThat(properties.maxRetries()).isEqualTo(3);
                    assertThat(properties.baseDelay()).isEqualTo(Duration.ofSeconds(1));
                    assertThat(properties.maxDelay()).isEqualTo(Duration.ofSeconds(30));
                    assertThat(properties.jitter()).isEqualTo(Duration.ofSeconds(2));
                });
    }

    @Test
    void shouldBindDefaultPropertiesCorrectly() {
        this.contextRunner
                .run(context -> {
                    assertThat(context).hasSingleBean(RetryProperties.class);

                    RetryProperties properties = context.getBean(RetryProperties.class);
                    assertThat(properties.maxRetries()).isEqualTo(5);
                    assertThat(properties.baseDelay()).isEqualTo(Duration.ofSeconds(2));
                    assertThat(properties.maxDelay()).isEqualTo(Duration.ofSeconds(60));
                    assertThat(properties.jitter()).isEqualTo(Duration.ofSeconds(3));
                });
    }


}
