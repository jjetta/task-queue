package com.jjetta.task_queue.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class TaskMetricsRecorderTest {

    private SimpleMeterRegistry registry;
    private TaskMetricsRecorder recorder;

    @BeforeEach
    void setUp() {
       registry = new SimpleMeterRegistry();
       recorder = new TaskMetricsRecorder(registry);
    }

    @Test
    void shouldAccuratelyRecordSuccessfulTaskCount() {
        final int numberOfSuccessfulTasks = 5;
        final String counterName = "tasks.processed";
        final String counterTagKey = "outcome";
        final String counterTagValue = "success";

        for (int i = 0; i < numberOfSuccessfulTasks; i++) {
            recorder.recordSuccess();
        }

        assertThat(registry.get(counterName).tag(counterTagKey, counterTagValue).counter().count())
                .isEqualTo(numberOfSuccessfulTasks);
    }

    @Test
    void shouldAccuratelyRecordFailedTaskCount() {
        final int numberOfFailedTasks = 5;
        final String counterName = "tasks.processed";
        final String counterTagKey = "outcome";
        final String counterTagValue = "failure";

        for (int i = 0; i < numberOfFailedTasks; i++) {
            recorder.recordFailure();
        }

        assertThat(registry.get(counterName).tag(counterTagKey, counterTagValue).counter().count())
                .isEqualTo(numberOfFailedTasks);
    }

    @Test
    void shouldAccuratelyRecordTaskLatency() {
        final String timerName = "task.latency";
        final Instant startTime = Instant.now();
        final Instant endTime = startTime.plus(Duration.ofSeconds(5));
        final Duration elapsedTime = Duration.between(startTime, endTime);

        recorder.recordCompletedTaskLatency(startTime, endTime);

        assertThat(registry.get(timerName).timer().totalTime(TimeUnit.MILLISECONDS))
                .isEqualTo(elapsedTime.toMillis());
    }
}
