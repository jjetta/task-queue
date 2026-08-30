package com.jjetta.task_queue.controller;

import com.jjetta.task_queue.model.Task;
import com.jjetta.task_queue.service.TaskService;
import com.jjetta.task_queue.web.TaskClaimedDto;
import com.jjetta.task_queue.web.TaskCreationRequestDto;
import com.jjetta.task_queue.web.TaskCreationResponseDto;
import com.jjetta.task_queue.web.TaskReportDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.Optional;

@RestController
@RequestMapping("/v1/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    public ResponseEntity<TaskCreationResponseDto> createTask(@RequestBody @Valid TaskCreationRequestDto taskCreationRequest) {
        Task createdTask = taskService.createTask(taskCreationRequest.type(), taskCreationRequest.params());

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

    @GetMapping("/next")
    public ResponseEntity<TaskClaimedDto> pullNextTask(@RequestParam(name = "type") String type) {
        Optional<Task> optionalTask = taskService.pullAndClaimTask(type);
        if (optionalTask.isPresent()) {
            Task task = optionalTask.get();
            return ResponseEntity.ok(TaskClaimedDto.builder()
                    .id(task.getId())
                    .type(task.getType())
                    .params(task.getParams())
                    .build());
        } else {
            return ResponseEntity.noContent().build();
        }
    }

    @PostMapping("/{id}/report")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reportExecutionResult(@PathVariable Long id, @RequestBody @Valid TaskReportDto taskReport) {
        taskService.reportTaskOutcome(id, taskReport);
    }

}
