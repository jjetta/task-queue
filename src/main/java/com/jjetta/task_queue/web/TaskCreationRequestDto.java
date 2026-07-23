package com.jjetta.task_queue.web;

import com.jjetta.task_queue.model.TaskType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TaskCreationRequestDto(
        @NotNull(message = "Task type cannot be null")
        TaskType type,

        @NotBlank(message = "Payload cannot be empty or null")
        String payload
) {}
