package com.jjetta.task_queue.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

import java.util.Map;

@Builder
public record TaskCreationRequestDto(
        @NotBlank(message = "Task type cannot be null or blank")
        String type,

        Map<String, Object> params
) {}
