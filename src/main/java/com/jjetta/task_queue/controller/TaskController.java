package com.jjetta.task_queue.controller;

import com.jjetta.task_queue.dto.*;
import com.jjetta.task_queue.model.Task;
import com.jjetta.task_queue.service.TaskService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    static final Logger logger = LoggerFactory.getLogger(TaskController.class);
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

        logger.atInfo().log("Task created with id: {}", createdTask.getId());
        return ResponseEntity.created(location).body(new TaskCreationResponseDto(createdTask.getId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskViewDto> getTask(@PathVariable Long id) {
        Task task = taskService.getTaskById(id);
        return ResponseEntity.ok(new TaskViewDto(task));
    }

    @GetMapping("/next")
    public ResponseEntity<TaskClaimedDto> pullNextTask(@RequestParam(name = "type") String type) {
        Optional<Task> optionalTask = taskService.pullAndClaimTask(type);
        if (optionalTask.isPresent()) {
            Task task = optionalTask.get();

            logger.atInfo().log("Client executor claimed task with id: {}", task.getId());
            return ResponseEntity.ok(new TaskClaimedDto(task));
        } else {
            logger.atInfo().log("No tasks currently available of type {}", type);
            return ResponseEntity.noContent().build();
        }
    }

    @PostMapping("/{id}/report")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reportExecutionResult(@PathVariable Long id, @RequestBody @Valid TaskReportDto taskReport) {
        taskService.reportTaskOutcome(id, taskReport);
        logger.atInfo().log("Client executor reported {} on task with id: {}", taskReport.outcome(), id);
    }

    @GetMapping("/dead")
    public ResponseEntity<List<TaskViewDto>> getDeadTasks() {
        var deadTasks = taskService.getDeadTasks()
                .stream()
                .map(TaskViewDto::new)
                .toList();
        return ResponseEntity.ok(deadTasks);
    }

    @PostMapping("/{id}/replay")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void replayTask(@PathVariable Long id) {
        taskService.replayTask(id);
        logger.atInfo().log("Client replayed task with id: {}", id);
    }


}
