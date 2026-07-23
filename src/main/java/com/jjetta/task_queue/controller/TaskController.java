package com.jjetta.task_queue.controller;

import com.jjetta.task_queue.model.Task;
import com.jjetta.task_queue.service.TaskService;
import com.jjetta.task_queue.web.TaskCreationRequestDto;
import com.jjetta.task_queue.web.TaskCreationResponseDto;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    public ResponseEntity<TaskCreationResponseDto> createTask(@RequestBody @Valid TaskCreationRequestDto taskCreationRequestDto) {
        Task createdTask = taskService.createTask(taskCreationRequestDto.type(), taskCreationRequestDto.params());

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(createdTask.getId())
                .toUri();
        
        return ResponseEntity.created(location).body(new TaskCreationResponseDto(createdTask.getId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Task> getTask(@PathVariable Long id) {
        Task task = taskService.getTaskById(id);
        return ResponseEntity.ok(task);
    }

}
