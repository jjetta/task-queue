package com.jjetta.task_queue.service;

import com.jjetta.task_queue.config.RetryProperties;
import com.jjetta.task_queue.model.Task;
import com.jjetta.task_queue.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskSweeperService {

    private final TaskRepository taskRepository;
    private final RetryProperties retryProperties;

    public TaskSweeperService(TaskRepository taskRepository,  RetryProperties retryProperties) {
        this.taskRepository = taskRepository;
        this.retryProperties = retryProperties;
    }

    @Transactional
    public void timeoutStaleTask(Task task) {
        task.recordFailure(retryProperties.maxRetries(), retryProperties.baseDelay(), retryProperties.maxDelay(), retryProperties.jitter());
        taskRepository.save(task);
    }
}
