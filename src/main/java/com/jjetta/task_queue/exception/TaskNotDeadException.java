package com.jjetta.task_queue.exception;

import com.jjetta.task_queue.model.TaskStatus;

public class TaskNotDeadException extends IllegalStateException{

    public TaskNotDeadException(Long id,  TaskStatus status) {
        super("Tried to replay task with id " + id + ", but its status is " + status);
    }
}
