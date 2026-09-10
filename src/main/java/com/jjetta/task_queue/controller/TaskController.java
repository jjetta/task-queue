package com.jjetta.task_queue.controller;

import com.jjetta.task_queue.model.Task;
import com.jjetta.task_queue.service.TaskService;
import com.jjetta.task_queue.dto.TaskClaimedDto;
import com.jjetta.task_queue.dto.TaskCreationRequestDto;
import com.jjetta.task_queue.dto.TaskCreationResponseDto;
import com.jjetta.task_queue.dto.TaskReportDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
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
    public Task getTask(@PathVariable Long id) {
        return taskService.getTaskById(id);
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

    @GetMapping("/dead")
    public List<Task> getDeadTasks() {
        return taskService.getDeadTasks();
    }

    @PostMapping("/{id}/replay")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void replayTask(@PathVariable Long id) {
        taskService.replayTask(id);
    }


}
