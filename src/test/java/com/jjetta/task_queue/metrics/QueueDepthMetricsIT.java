package com.jjetta.task_queue.metrics;

import com.jjetta.task_queue.TestcontainersConfiguration;
import com.jjetta.task_queue.model.Task;
import com.jjetta.task_queue.model.TaskStatus;
import com.jjetta.task_queue.repository.TaskRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
public class QueueDepthMetricsIT {

    @Autowired
    MeterRegistry meterRegistry;

    @Autowired
    private TaskRepository taskRepository;

    @BeforeEach
    public void setup() {
        taskRepository.deleteAll();
        int numberOfPendingTasks = 5;
        int numberOfRunningTasks = 5;

        for (int i = 0; i < numberOfPendingTasks; i++) {
            Task task = Task.createTask("background-job", Map.of("arg1", "param1"));
            taskRepository.save(task);
        }

        for (int i = 0; i < numberOfRunningTasks; i++) {
            Task task = Task.createTask("background-job", Map.of("arg1", "param1"));
            ReflectionTestUtils.setField(task, "status", TaskStatus.RUNNING);
            taskRepository.save(task);
        }
    }

    @Test
    public void shouldMeasureQueueDepthCorrectly() {
        final int numberOfPendingTasks = 5;
        final String gaugeName = "queue.depth";
        assertThat(meterRegistry.get(gaugeName).gauge().value()).isEqualTo(numberOfPendingTasks);
    }

}
