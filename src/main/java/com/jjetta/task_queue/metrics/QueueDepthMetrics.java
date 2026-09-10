package com.jjetta.task_queue.metrics;

import com.jjetta.task_queue.model.TaskStatus;
import com.jjetta.task_queue.repository.TaskRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

@Component
public class QueueDepthMetrics implements MeterBinder {

    private final TaskRepository taskRepository;

    public QueueDepthMetrics(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Override
    public void bindTo(MeterRegistry meterRegistry) {
        final String gaugeName = "queue.depth";
        Gauge.builder(gaugeName, () -> taskRepository.countByStatus(TaskStatus.PENDING))
                .description("Current number of PENDING tasks")
                .register(meterRegistry);
    }
}
