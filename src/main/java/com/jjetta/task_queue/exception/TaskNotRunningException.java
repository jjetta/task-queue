package com.jjetta.task_queue.exception;

import com.jjetta.task_queue.model.TaskStatus;

public class TaskNotRunningException extends IllegalStateException {

    public TaskNotRunningException(Long id, TaskStatus status) {
        super("Attempt to report execution outcome on task with id: " + id + ", but said task is " + status);
    }
}