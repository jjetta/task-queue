package com.jjetta.task_queue.service;

import com.jjetta.task_queue.TestcontainersConfiguration;
import com.jjetta.task_queue.model.Task;
import com.jjetta.task_queue.model.TaskStatus;
import com.jjetta.task_queue.repository.TaskRepository;
import com.jjetta.task_queue.dto.TaskReportDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest()
@ActiveProfiles("test")
public class TaskSweeperIT {

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private TaskSweeperService taskSweeperService;

    @Autowired
    private TaskSweeper taskSweeper;

    @Autowired
    private TaskService taskService;

    @BeforeEach
    public void setup() {
        taskRepository.deleteAll();

        Task staleTask = Task.createTask("stale-job", Map.of("arg1", "param1"));
        ReflectionTestUtils.setField(staleTask, "status", TaskStatus.RUNNING);
        ReflectionTestUtils.setField(staleTask, "claimedAt", Instant.now().minusSeconds(30));
        ReflectionTestUtils.setField(staleTask, "claimToken", UUID.randomUUID());

        taskRepository.save(staleTask);
    }

    @Test
    public void shouldTimeoutTasksSuccessfully() {
        taskSweeper.timeoutStaleRunningTasks();
        List<Task> tasks = taskRepository.findAll();

        for (Task task : tasks) {
            assertThat(task.getFailureCount()).isEqualTo(1);
            assertThat(task.getClaimedAt()).isNull();
            assertThat(task.getClaimToken()).isNull();
        }
    }

    @Test
    public void shouldNotTimeoutTaskIfItHasBeenReported() throws Exception {
        Task task =  Task.createTask("password", Map.of("arg1", "param1"));
        taskRepository.save(task);

        String type = task.getType();
        Optional<Task> taskA = taskService.pullAndClaimTask(type);
        assertThat(taskA.isPresent()).isTrue();


        Long taskId = taskA.get().getId();
        UUID reportToken = taskA.get().getClaimToken();

        TaskReportDto taskReport = TaskReportDto.builder()
                .outcome(TaskReportDto.Outcome.SUCCESS)
                .claimToken(reportToken)
                .build();

        Optional<Task> taskB = taskRepository.findById(taskId);
        assertThat(taskB.isPresent()).isTrue();
        taskService.reportTaskOutcome(taskId, taskReport);

        assertThatThrownBy(() -> taskSweeperService.timeoutStaleTask(taskB.get()))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    public void shouldEvictTaskSuccessfully() {
        // Tasks eligible for eviction meet the following criteria:
        // - they have a PENDING status
        // - they have a failureCount of ZERO
        // - they are at least one minute old

        // This task is PENDING, has ZERO failures, and is outside the creation window
        // Therefore, it should get evicted
        Task taskA =  Task.createTask("unclaimed-job", Map.of("arg1", "param1"));
        ReflectionTestUtils.setField(taskA, "createdAt", Instant.now().minusSeconds(61));
        Long taskAId = taskRepository.save(taskA).getId();

        // This task is PENDING and is outside the creation window, but has failed once
        // Therefore, it should NOT get evicted
        Task taskB =  Task.createTask("unclaimed-job", Map.of("arg1", "param1"));
        ReflectionTestUtils.setField(taskB, "createdAt", Instant.now().minusSeconds(61));
        ReflectionTestUtils.setField(taskB, "failureCount", 1);
        Long taskBId = taskRepository.save(taskB).getId();

        // This task may be outside the creation window, but is RUNNING
        // Therefore, it should NOT get evicted
        Task taskC = Task.createTask("unclaimed-job", Map.of("arg1", "param1"));
        ReflectionTestUtils.setField(taskC, "createdAt", Instant.now().minusSeconds(61));
        ReflectionTestUtils.setField(taskC, "status", TaskStatus.RUNNING);
        Long taskCId = taskRepository.save(taskC).getId();


        // This task is within the creation window
        // Therefore, it should NOT get evicted
        Task taskD = Task.createTask("unclaimed-job", Map.of("arg1", "param1"));
        ReflectionTestUtils.setField(taskD, "createdAt", Instant.now().minusSeconds(55));
        Long taskDId = taskRepository.save(taskD).getId();

        taskSweeper.evictUnclaimedTasks();

        Optional<Task> resultTaskA =  taskRepository.findById(taskAId);
        assertThat(resultTaskA.isPresent()).isTrue();
        assertThat(resultTaskA.get().getStatus()).isEqualTo(TaskStatus.DEAD);
        assertThat(resultTaskA.get().getFailureCount()).isZero();

        Optional<Task> resultTaskB =  taskRepository.findById(taskBId);
        assertThat(resultTaskB.isPresent()).isTrue();
        assertThat(resultTaskB.get().getStatus()).isEqualTo(TaskStatus.PENDING);

        Optional<Task> resultTaskC =  taskRepository.findById(taskCId);
        assertThat(resultTaskC.isPresent()).isTrue();
        assertThat(resultTaskC.get().getStatus()).isEqualTo(TaskStatus.RUNNING);

        Optional<Task> resultTaskD =  taskRepository.findById(taskDId);
        assertThat(resultTaskD.isPresent()).isTrue();
        assertThat(resultTaskD.get().getStatus()).isEqualTo(TaskStatus.PENDING);
    }
}
