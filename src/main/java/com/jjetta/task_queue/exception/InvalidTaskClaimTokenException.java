package com.jjetta.task_queue.exception;

import java.util.UUID;

public class InvalidTaskClaimTokenException extends IllegalStateException {

    public InvalidTaskClaimTokenException(Long id, UUID uuid) {
        super("Invalid task claim token: " + uuid + " for task with id: " + id);
    }
}
