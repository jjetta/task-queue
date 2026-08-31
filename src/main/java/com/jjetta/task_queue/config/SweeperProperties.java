package com.jjetta.task_queue.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.sweeper")
public record SweeperProperties(
        Duration interval,
        Duration taskTimeout
) {}