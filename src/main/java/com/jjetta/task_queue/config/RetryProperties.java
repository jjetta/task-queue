package com.jjetta.task_queue.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.retry")
public record RetryProperties(
        @DefaultValue("5")  int maxRetries,
        @DefaultValue("2s")  Duration baseDelay,
        @DefaultValue("60s") Duration maxDelay,
        @DefaultValue("3s")  Duration jitter
) {}
