package com.jjetta.task_queue.model;

import java.util.EnumSet;

public enum TaskStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    DEAD;

    private EnumSet<TaskStatus> transitions;

    static {
        PENDING.transitions = EnumSet.of(RUNNING);
        RUNNING.transitions = EnumSet.of(PENDING, COMPLETED, DEAD);
        DEAD.transitions = EnumSet.of(PENDING);

        COMPLETED.transitions = EnumSet.noneOf(TaskStatus.class);
    }

    public boolean canTransitionTo(TaskStatus newStatus) {
        return this.transitions.contains(newStatus);
    }

}
