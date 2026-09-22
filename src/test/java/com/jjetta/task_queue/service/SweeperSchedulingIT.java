package com.jjetta.task_queue.service;

import com.jjetta.task_queue.TestcontainersConfiguration;
import com.jjetta.task_queue.model.Task;
import com.jjetta.task_queue.model.TaskStatus;
import com.jjetta.task_queue.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Unlike TaskSweeperIT, this test never calls TaskSweeper's methods directly.
 * It proves the sweeper is actually wired to Spring's scheduler (@EnableScheduling),
 * not just that its logic is correct in isolation. The interval/timeout are set
 * very short so a healthy run finishes in well under a second; the 15s budget
 * below is slack for a shared/loaded test JVM, not the expected latency.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.sweeper.interval=200ms",
        "app.sweeper.task-timeout=100ms"
})
class SweeperSchedulingIT {

    @Autowired
    private TaskRepository taskRepository;

    @Test
    void sweeperRunsOnItsOwnSchedule() {
        Task staleTask = Task.createTask("scheduling-check", Map.of());
        ReflectionTestUtils.setField(staleTask, "status", TaskStatus.RUNNING);
        ReflectionTestUtils.setField(staleTask, "claimedAt", Instant.now().minusSeconds(5));
        ReflectionTestUtils.setField(staleTask, "claimToken", UUID.randomUUID());
        Long id = taskRepository.save(staleTask).getId();

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            Task reloaded = taskRepository.findById(id).orElseThrow();
            assertThat(reloaded.getStatus()).isEqualTo(TaskStatus.PENDING);
            assertThat(reloaded.getFailureCount()).isEqualTo(1);
        });
    }
}
