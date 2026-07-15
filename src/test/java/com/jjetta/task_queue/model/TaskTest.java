package com.jjetta.task_queue.model;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;

import static com.jjetta.task_queue.model.Task.calculateNextRetryAt;
import static com.jjetta.task_queue.model.Task.createTask;
import static java.time.Instant.now;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.assertj.core.api.BDDAssertions.within;

public class TaskTest {

    @Test
    void createTaskWithNullType() {
        assertThatThrownBy(() -> {
            createTask(null, "payload");
        })
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Task type cannot be null");
    }

    @Test
    void createTaskWithNullPayload() {
        assertThatThrownBy(() -> {
            createTask(TaskType.SLEEP_SUCCEED, null);
        })
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Payload cannot be null");
    }

    @Test
    void createTaskWithBlankPayload() {
        assertThatThrownBy(() -> {
            createTask(TaskType.SLEEP_SUCCEED, "");
        })
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Payload cannot be blank");
    }

    @Test
    void validTransition() {
        Task task = createTask(TaskType.SLEEP_SUCCEED, "payload");
        task.transitionTo(TaskStatus.RUNNING);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.RUNNING);
    }

    @Test
    void invalidTransition() {
        Task task = createTask(TaskType.SLEEP_SUCCEED, "payload");
        TaskStatus expectedStatus = task.getStatus();
        assertThatThrownBy(() -> {
            task.transitionTo(TaskStatus.COMPLETED);
        })
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot transition from " + expectedStatus + " to " + TaskStatus.COMPLETED);
        assertThat(task.getStatus()).isEqualTo(expectedStatus);
    }

    @ParameterizedTest
    @CsvSource({
            "0, 2",
            "1, 4",
            "2, 8",
            "3, 16",
            "4, 32",
            "5, 64"
    })
    void shouldDoubleDelayWithEachFailure(int failureCount, long expectedDelay) {
        Duration base = Duration.ofSeconds(2);
        Duration maxDelay = Duration.ofSeconds(100);
        Duration jitterWindow = Duration.ZERO;
        Duration expectedDelayDuration = Duration.ofSeconds(expectedDelay);

        Instant backoff = calculateNextRetryAt(base, maxDelay, jitterWindow, failureCount);
        assertThat(backoff).isCloseTo(Instant.now().plus(expectedDelayDuration), within(1, ChronoUnit.SECONDS));
    }

    @ParameterizedTest
    @CsvSource({
            "0, 2",
            "1, 4",
            "2, 8",
            "3, 10",
            "4, 10",
            "5, 10"
    })
    void shouldCapAtMaxDelay(int failureCount, long expectedDelay) {
        Duration base = Duration.ofSeconds(2);
        Duration maxDelay = Duration.ofSeconds(10);
        Duration jitterWindow = Duration.ZERO;
        Duration expectedDelayDuration = Duration.ofSeconds((expectedDelay));

        Instant backoff = calculateNextRetryAt(base, maxDelay, jitterWindow, failureCount);
        Instant now = Instant.now();
        assertThat(backoff).isCloseTo(now.plus(expectedDelayDuration), within(1, ChronoUnit.SECONDS));
    }

    @RepeatedTest(5)
    void shouldAddJitterWithinBounds() {
        Duration base = Duration.ofSeconds(2);
        Duration maxDelay = Duration.ofSeconds(100);
        Duration jitterWindow = Duration.ofSeconds(3);

        Instant now = Instant.now();
        Instant backoff = calculateNextRetryAt(base, maxDelay, jitterWindow, 3);
        Duration minBackOff = Duration.ofSeconds(16);
        Duration maxBackOff = Duration.ofSeconds(19);

        assertThat(backoff).isBetween(now.plus(minBackOff), now.plus(maxBackOff));
    }

    @Test
    void shouldNotAddJitterWhenWindowIsZero() {
        Duration base = Duration.ofSeconds(2);
        Duration maxDelay = Duration.ofSeconds(100);
        Duration jitterWindow = Duration.ZERO;

        Instant now = Instant.now();
        Instant backoff = calculateNextRetryAt(base, maxDelay, jitterWindow, 3);
        assertThat(backoff).isCloseTo(now.plus(Duration.ofSeconds(16)), within(1, ChronoUnit.SECONDS));
    }

    @Test
    void replay() {}
}
