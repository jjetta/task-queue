package com.jjetta.task_queue.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
@Table(name = "tasks")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false)
    private String type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> params;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "task_status")
    private TaskStatus status;

    @Column(nullable = false)
    private Integer failureCount;

    @Column(nullable = false)
    private Instant createdAt;

    @Column
    private Instant claimedAt;

    @Column
    private Instant completedAt;

    @Column
    private Instant nextRetryAt;

    @Column
    private UUID claimToken;

    public static Task createTask(String type, Map<String, Object> params) {
        Objects.requireNonNull(type, "Task type cannot be null");

        if (type.isBlank()) {
            throw new IllegalArgumentException("Task type cannot be blank");
        }

        if (params == null || params.isEmpty()) {
            params = Map.of();
        }

        return new Task(type, params);
    }

    private Task(String type, Map<String, Object> params) {
        this.id = null;
        this.type = type;
        this.params = params;
        this.status = TaskStatus.PENDING;
        this.failureCount = 0;
        this.createdAt = Instant.now();
        this.claimedAt = null;
        this.completedAt = null;
        this.nextRetryAt = null;
        this.claimToken = null;
    }

    /** Mutates the status of a task using the state transition table defined in
     *  TaskStatus.java.
     *
     * @param newStatus the status the Task is transitioning to
     */
    public void transitionTo(TaskStatus newStatus) {
        if (this.status.canTransitionTo(newStatus)) {
            this.status = newStatus;
        } else {
            throw new IllegalStateException("Cannot transition from " + this.status  + " to " + newStatus);
        }
    }

    public void recordSuccess() {
        this.transitionTo(TaskStatus.COMPLETED);
        this.completedAt = Instant.now();
        this.releaseClaim();
    }

    /** Increments a Task's failure count by 1. If maxRetries has been reached,
     *  the Task is then moved to the DEAD (DLQ) state. Else, the nextRetryAt is
     *  recalculated.
     *
     * @param maxRetries the maximum amount of times the Task may be retried
     * @param baseDelay the base Duration a task should delay
     * @param maxDelay the maximum Duration a task should delay
     * @param jitterWindow the window of time for jitter
     */
    public void recordFailure(int maxRetries,
                              Duration baseDelay,
                              Duration maxDelay,
                              Duration jitterWindow) {
        this.failureCount++;
        if (this.failureCount >= maxRetries) {
            this.transitionTo(TaskStatus.DEAD);
            this.nextRetryAt = null;
        } else {
            this.transitionTo(TaskStatus.PENDING);
            this.nextRetryAt = calculateNextRetryAt(baseDelay, maxDelay, jitterWindow, this.failureCount);
        }
        this.releaseClaim();
    }

    /** Calculate when a task is to be retried upon failure (exponential backoff).
     *
     * @param baseDelay base Duration for exponential backoff
     * @param maxDelay the maximum Duration to delay retry
     * @param jitterWindow the upper bound of jitter time
     * @param failureCount the current number of times a Task has failed
     *
     * @return an Instant representing the next Instant a Task is to be retried
     */
    public static Instant calculateNextRetryAt(Duration baseDelay,
                                               Duration maxDelay,
                                               Duration jitterWindow,
                                               int failureCount) {
        long exponent = 1L << failureCount;
        Duration exponentialBackoff = baseDelay.multipliedBy(exponent);

        int delayComparison = maxDelay.compareTo(exponentialBackoff);
        Duration delay = (delayComparison > 0) ? exponentialBackoff : maxDelay;

        Duration jitter;
        if (jitterWindow.isZero()) {
            jitter = Duration.ZERO;
        } else {
            jitter = Duration.ofSeconds(ThreadLocalRandom.current().nextLong(0, jitterWindow.getSeconds()));
        }
        delay = delay.plus(jitter);

        return Instant.now().plus(delay);
    }

    public void releaseClaim() {
        this.claimToken = null;
        this.claimedAt = null;
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
