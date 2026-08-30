package com.jjetta.task_queue.service;

import com.jjetta.task_queue.config.RetryProperties;
import com.jjetta.task_queue.exception.InvalidTaskClaimTokenException;
import com.jjetta.task_queue.exception.TaskNotFoundException;
import com.jjetta.task_queue.exception.TaskNotRunningException;
import com.jjetta.task_queue.model.Task;
import com.jjetta.task_queue.model.TaskStatus;
import com.jjetta.task_queue.repository.TaskRepository;
import com.jjetta.task_queue.web.TaskReportDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final RetryProperties retryProperties;

    public TaskService(TaskRepository taskRepository, RetryProperties retryProperties) {
        this.taskRepository = taskRepository;
        this.retryProperties = retryProperties;
    }

    public Task createTask(String type, Map<String, Object> params) {
        Task createdTask = Task.createTask(type, params);
        createdTask = taskRepository.save(createdTask);
        return createdTask;
    }

    public Task getTaskById(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
    }

    @Transactional
    public Optional<Task> pullAndClaimTask(String type) {
        Optional<Task> pendingTask = taskRepository.findNextTask(type);
        if (pendingTask.isEmpty()) {
            return Optional.empty();
        }

        Task task = pendingTask.get();

        int rowsAffected = taskRepository.claimTask(task.getId());
        if (rowsAffected == 0) {
            throw new IllegalStateException("Failed to claim task " + task.getId());
        }

        return Optional.of(taskRepository.findById(task.getId())
                .orElseThrow(() -> new IllegalStateException("Pending task discovered, locked, and claimed, but not found.")));
    }

    public void reportTaskOutcome(Long id, TaskReportDto taskReport) {
        Task executedTask = getTaskById(id);
        if (executedTask.getStatus() != TaskStatus.RUNNING) {
            throw new TaskNotRunningException(id, executedTask.getStatus());
        }

        if (!taskReport.claimToken().equals(executedTask.getClaimToken())) {
            throw new InvalidTaskClaimTokenException(id, taskReport.claimToken());
        }

        if (taskReport.outcome() == TaskReportDto.Outcome.SUCCESS) {
            executedTask.recordSuccess();
        } else {
            executedTask.recordFailure(retryProperties.maxRetries(), retryProperties.baseDelay(), retryProperties.maxDelay(), retryProperties.jitter());
        }
        taskRepository.save(executedTask);
    }
}
