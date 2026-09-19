package com.jjetta.task_queue.dto;

import com.jjetta.task_queue.model.Task;
import lombok.Builder;

import java.util.Map;
import java.util.UUID;

@Builder
public record TaskClaimedDto(
        Long id,
        String type,
        Map<String, Object> params,
        UUID claimToken
) {
    public TaskClaimedDto(Task task) {
        this(
                task.getId(),
                task.getType(),
                task.getParams(),
                task.getClaimToken()
        );
    }
}
