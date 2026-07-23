package com.jjetta.task_queue.web;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

import java.util.Map;

@Builder
public record TaskCreationRequestDto(
        @NotBlank(message = "Task type cannot be null")
        String type,

        Map<String, Object> params
) {}
