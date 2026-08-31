package com.jjetta.task_queue.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.retry")
public record RetryProperties(
        int maxRetries,
        Duration baseDelay,
        Duration maxDelay,
        Duration jitter
) {}
