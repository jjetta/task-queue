package com.jjetta.task_queue.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class TaskMetricsRecorder {

    private final Counter successfulTasksCounter;
    private final Counter failedTasksCounter;
    private final Timer taskLatencyTimer;

    public TaskMetricsRecorder(MeterRegistry meterRegistry) {
        this.successfulTasksCounter = Counter
                .builder("tasks.processed")
                .description("Total number of processed tasks")
                .tag("outcome", "success")
                .register(meterRegistry);

        this.failedTasksCounter = Counter
                .builder("tasks.processed")
                .description("Total number of processed tasks")
                .tag("outcome", "failure")
                .register(meterRegistry);

        this.taskLatencyTimer = Timer
                .builder("task.latency")
                .description("Measures task latency")
                .register(meterRegistry);
    }

    public void recordSuccess() {
        this.successfulTasksCounter.increment();
    }

    public void recordFailure() {
        this.failedTasksCounter.increment();
    }

    public void recordCompletedTaskLatency(Instant start, Instant finish) {
        this.taskLatencyTimer.record(Duration.between(start, finish));
    }

}
