package com.jjetta.task_queue.dto;

import com.jjetta.task_queue.model.Task;
import com.jjetta.task_queue.model.TaskStatus;
import lombok.Builder;

import java.time.Instant;
import java.util.Map;

@Builder
public record TaskViewDto(
        Long id,
        String type,
        Map<String, Object> params,
        TaskStatus status,
        Integer failureCount,
        Instant createdAt,
        Instant claimedAt,
        Instant completedAt,
        Instant nextRetryAt
) {
    public TaskViewDto(Task task) {
        this(
                task.getId(),
                task.getType(),
                task.getParams(),
                task.getStatus(),
                task.getFailureCount(),
                task.getCreatedAt(),
                task.getClaimedAt(),
                task.getCompletedAt(),
                task.getNextRetryAt()
        );
    }
}
