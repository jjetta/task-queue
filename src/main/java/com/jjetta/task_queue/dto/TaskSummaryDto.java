package com.jjetta.task_queue.dto;

import lombok.Builder;

@Builder
public record TaskSummaryDto(
        Long id,
        String type
) {}
