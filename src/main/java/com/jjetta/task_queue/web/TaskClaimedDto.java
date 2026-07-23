package com.jjetta.task_queue.web;

import lombok.Builder;

import java.util.Map;

@Builder
public record TaskClaimedDto(
        Long id,
        String type,
        Map<String, Object> params
) {}
