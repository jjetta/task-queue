package com.jjetta.task_queue.service;

import com.jjetta.task_queue.exception.TaskNotFoundException;
import com.jjetta.task_queue.model.Task;
import com.jjetta.task_queue.repository.TaskRepository;
import com.jjetta.task_queue.web.TaskCreationRequestDto;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class TaskService {

    private final TaskRepository taskRepository;

    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
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
}
