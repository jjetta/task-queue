package com.jjetta.task_queue.service;

import com.jjetta.task_queue.exception.InvalidTaskClaimTokenException;
import com.jjetta.task_queue.exception.TaskNotRunningException;
import com.jjetta.task_queue.model.Task;
import com.jjetta.task_queue.model.TaskStatus;
import com.jjetta.task_queue.repository.TaskRepository;
import com.jjetta.task_queue.TestcontainersConfiguration;
import com.jjetta.task_queue.web.TaskReportDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class TaskServiceIT {

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private TaskService taskService;

    @BeforeEach
    public void setup() {
        taskRepository.deleteAll();
        int numberOfTasks = 10;

        for (int i = 0; i < numberOfTasks; i++) {
            Task task = Task.createTask("background-job", Map.of("arg1", "param1"));
            taskRepository.save(task);
        }
    }

    @RepeatedTest(value = 10)
    public void shouldPullAndClaimTaskConcurrently() throws Exception {
        int numberOfThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(1);

        String typeParam = "background-job";
        Set<Long> taskSet = new HashSet<>();

        List<Future<Optional<Task>>> futures = new ArrayList<>();
        for (int i = 0; i < numberOfThreads; i++) {
            futures.add(executor.submit(() -> {
                try {
                    latch.await();
                    return taskService.pullAndClaimTask(typeParam);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
        }

        latch.countDown();

        for (Future<Optional<Task>> future : futures) {
            try {
                Optional<Task> optionalTask = future.get();
                assertThat(optionalTask).isPresent();
                taskSet.add(optionalTask.get().getId());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        assertThat(taskSet.size()).isEqualTo(numberOfThreads);
    }

    @Test
    public void shouldReportTaskOutcomeSuccessfully() throws Exception {
        String type = "background-job";
        Optional<Task> optionalTask = taskService.pullAndClaimTask(type);
        assertThat(optionalTask).isPresent();

        Task task = optionalTask.get();
        Long taskId = task.getId();

        UUID reportToken = task.getClaimToken();

        TaskReportDto taskReport = TaskReportDto.builder()
                .outcome(TaskReportDto.Outcome.SUCCESS)
                .claimToken(reportToken)
                .build();

        taskService.reportTaskOutcome(taskId, taskReport);

        Task refetchedTask = taskService.getTaskById(taskId);

        assertThat(refetchedTask.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(refetchedTask.getCompletedAt()).isNotNull();
        assertThat(refetchedTask.getClaimToken()).isNull();
    }

    @Test
    public void shouldThrowInvalidTaskClaimTokenExceptionWhenReportingTaskOutcome() throws Exception {
        String type = "background-job";
        Optional<Task> optionalTask = taskService.pullAndClaimTask(type);
        assertThat(optionalTask).isPresent();

        Task task = optionalTask.get();
        Long taskId = task.getId();
        UUID claimToken = task.getClaimToken();

        UUID reportToken = UUID.randomUUID();

        TaskReportDto taskReport = TaskReportDto.builder()
                .outcome(TaskReportDto.Outcome.SUCCESS)
                .claimToken(reportToken)
                .build();

        assertThatThrownBy(() -> taskService.reportTaskOutcome(taskId, taskReport))
                .isInstanceOf(InvalidTaskClaimTokenException.class)
                .hasMessageContaining("Invalid task claim token: " + reportToken + " for task with id: " + taskId);

        Task refetchedTask = taskService.getTaskById(taskId);

        assertThat(refetchedTask.getStatus()).isEqualTo(TaskStatus.RUNNING);
        assertThat(refetchedTask.getClaimToken()).isEqualTo(claimToken);
    }

    @Test
    public void shouldThrowTaskNotRunningExceptionWhenReportingTaskOutcome() throws Exception {
        List<Task> tasks = taskRepository.findAll();
        Task task = tasks.get(0);

        TaskReportDto taskReport = TaskReportDto.builder()
                .outcome(TaskReportDto.Outcome.SUCCESS)
                .claimToken(UUID.randomUUID())
                .build();

        assertThatThrownBy(() -> taskService.reportTaskOutcome(task.getId(), taskReport))
                .isInstanceOf(TaskNotRunningException.class)
                .hasMessageContaining("Attempt to report execution outcome on task with id: " + task.getId() + ", but said task is " + task.getStatus());

        Task refetchedTask = taskService.getTaskById(task.getId());

        assertThat(refetchedTask.getStatus()).isEqualTo(TaskStatus.PENDING);
    }
}