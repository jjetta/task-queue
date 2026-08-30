package com.jjetta.task_queue.web;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.util.UUID;

@Builder
public record TaskReportDto(
        @NotNull(message = "Task outcome cannot be null")
        Outcome outcome,

        @NotNull(message = "Task report token cannot be null")
        UUID claimToken
) {
    public enum Outcome {
        SUCCESS,
        FAILURE
    }
}
