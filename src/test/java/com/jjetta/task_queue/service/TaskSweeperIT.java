package com.jjetta.task_queue.service;

import com.jjetta.task_queue.TestcontainersConfiguration;
import com.jjetta.task_queue.model.Task;
import com.jjetta.task_queue.model.TaskStatus;
import com.jjetta.task_queue.repository.TaskRepository;
import com.jjetta.task_queue.web.TaskReportDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
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

        Task task = Task.createTask("stale-job", Map.of("arg1", "param1"));
        ReflectionTestUtils.setField(task, "status", TaskStatus.RUNNING);
        ReflectionTestUtils.setField(task, "claimedAt", Instant.now().minusSeconds(30));
        ReflectionTestUtils.setField(task, "claimToken", UUID.randomUUID());

        taskRepository.save(task);
    }

    @Test
    public void shouldTimeoutTasksSuccessfully() {
        taskSweeper.timeoutStaleTasks();
        List<Task> tasks = taskRepository.findAll();

        for (Task task : tasks) {
            assertThat(task.getFailureCount()).isEqualTo(1);
            assertThat(task.getClaimedAt()).isNull();
            assertThat(task.getClaimToken()).isNull();
        }
    }

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
        taskService.reportTaskOutcome(taskId, taskReport);

        assertThatThrownBy(() -> taskSweeperService.timeoutStaleTask(taskB.get()))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }
}
