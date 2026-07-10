package com.jjetta.task_queue.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.Objects;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "tasks")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TaskStatus status;

    @Column(nullable = false)
    private Integer failureCount;

    @Column
    private Instant nextRetryAt;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TaskType type;

    @Column(nullable = false)
    private String payload;

    @Column(nullable = false)
    private Instant createdAt;

    @Column
    private Instant claimedAt;

    @Column
    private Instant completedAt;

    public static Task createTask(TaskType type, String payload) {
        Objects.requireNonNull(type, "Task type cannot be null");
        Objects.requireNonNull(payload, "Payload cannot be null");
        if (payload.isBlank()) {
            throw new IllegalArgumentException("Payload cannot be blank");
        }
        return new Task(type, payload);
    }

    private Task(TaskType type, String payload) {
        this.id = null;
        this.status = TaskStatus.PENDING;
        this.failureCount = 0;
        this.nextRetryAt = null;
        this.type = type;
        this.payload = payload;
        this.createdAt = Instant.now();
        this.claimedAt = null;
        this.completedAt = null;
    }

    /** Mutates the status of a task using the state transition table defined in
     *  TaskStatus.java.
     *
     * @param newStatus the status the Task is transitioning to.
     */
    public void transitionTo(TaskStatus newStatus) {
        if (this.status.canTransitionTo(newStatus)) {
            this.status = newStatus;
        } else {
            throw new IllegalStateException("Cannot transition from " + this.status  + " to " + newStatus);
        }
    }

    /** In the event a task is in a DEAD state, this method resets its status to
     *  PENDING and its failure count to zero.
     *
     */
    public void replay() {
        if (this.status != TaskStatus.DEAD) {
            throw new IllegalStateException("Only Tasks in a DEAD state can be replayed.");
        }
        this.transitionTo(TaskStatus.PENDING);
        this.failureCount = 0;
        this.nextRetryAt = null;
    }
}
