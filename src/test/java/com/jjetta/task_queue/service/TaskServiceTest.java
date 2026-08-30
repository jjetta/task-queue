package com.jjetta.task_queue.service;

import com.jjetta.task_queue.config.RetryProperties;
import com.jjetta.task_queue.exception.InvalidTaskClaimTokenException;
import com.jjetta.task_queue.exception.TaskNotFoundException;
import com.jjetta.task_queue.exception.TaskNotRunningException;
import com.jjetta.task_queue.model.TaskStatus;
import com.jjetta.task_queue.repository.TaskRepository;
import com.jjetta.task_queue.model.Task;
import com.jjetta.task_queue.web.TaskReportDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.swing.*;
import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
public class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private TaskService taskService;

    private Task task;

    @BeforeEach
    void setUp() {
        RetryProperties retryProperties = new RetryProperties(
                3,
                Duration.ofSeconds(2),
                Duration.ofSeconds(60),
                Duration.ofSeconds(3)
        );

        taskService = new TaskService(taskRepository, retryProperties);
    }

    @Test
    public void shouldCreateTaskSuccessfully() {
        String type = "background-job";
        Map<String, Object> params = new HashMap<>();

        ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);

        Mockito.when(taskRepository.save(Mockito.any(Task.class))).thenAnswer(AdditionalAnswers.returnsFirstArg());

        Task testTask = taskService.createTask(type, params);

        Mockito.verify(taskRepository).save(taskCaptor.capture());
        Task createdTask = taskCaptor.getValue();

        assertThat(createdTask.getType()).isEqualTo(type);
        assertThat(createdTask.getParams()).isEqualTo(params);
    }

    @Test
    public void shouldGetTaskSuccessfully() {
        Task existingTask = Task.createTask("background-job", Map.of());
        ReflectionTestUtils.setField(existingTask, "id", 1L);

        Mockito.when(taskRepository.findById(1L)).thenReturn(Optional.of(existingTask));

        Task foundTask = taskService.getTaskById(1L);

        assertThat(foundTask).isEqualTo(existingTask);
        Mockito.verify(taskRepository).findById(1L);
    }

    @Test
    public void shouldThrowTaskNotFoundExceptionWhenGettingTask() {
        Long id = 1L;

        Mockito.when(taskRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.getTaskById(id))
                .isInstanceOf(TaskNotFoundException.class)
                .hasMessageContaining("Task with id " + id + " not found");

        Mockito.verify(taskRepository).findById(Mockito.anyLong());
    }

    @Test
    public void shouldPullAndClaimTaskSuccessfully() {
        String typeParam = "background-job";
        Long id = 3L;

        Task testNextFoundTask = Task.createTask(typeParam, Map.of());
        ReflectionTestUtils.setField(testNextFoundTask, "id", id);

        Mockito.when(taskRepository.findNextTask(typeParam))
                .thenReturn(Optional.of(testNextFoundTask));

        Mockito.when(taskRepository.claimTask(Mockito.anyLong()))
                .thenReturn(1);

        Mockito.when(taskRepository.findById(id))
                .thenReturn(Optional.of(testNextFoundTask));

        Optional<Task> result = taskService.pullAndClaimTask(typeParam);

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(testNextFoundTask);

        Mockito.verify(taskRepository).findNextTask(typeParam);
        Mockito.verify(taskRepository).claimTask(id);
        Mockito.verify(taskRepository).findById(id);
    }

    @Test
    public void shouldPullAndClaimEmptyTaskSuccessfully() {
        String typeParam = "background-job";
        Mockito.when(taskRepository.findNextTask(Mockito.anyString()))
                .thenReturn(Optional.empty());

        Optional<Task> result = taskService.pullAndClaimTask(typeParam);

        assertThat(result).isEmpty();

        Mockito.verify(taskRepository).findNextTask(Mockito.anyString());
        Mockito.verifyNoMoreInteractions(taskRepository);
    }

    @Test
    public void shouldThrowIllegalStateExceptionWhenTaskClaimFails() {
        String typeParam = "background-job";
        Long id = 3L;

        Task testTask = Task.createTask(typeParam, Map.of());
        ReflectionTestUtils.setField(testTask, "id", id);

        Mockito.when(taskRepository.findNextTask(typeParam))
                .thenReturn(Optional.of(testTask));

        Mockito.when(taskRepository.claimTask(id))
                .thenReturn(0);

        assertThatThrownBy(() -> taskService.pullAndClaimTask(typeParam))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to claim task " + id);

        Mockito.verify(taskRepository, Mockito.never()).findById(Mockito.anyLong());
    }

    @Test
    public void shouldThrowIllegalStateExceptionWhenTaskVanishesAfterClaiming() {
        String typeParam = "background-job";
        Long id = 3L;

        Task testTask = Task.createTask(typeParam, Map.of());
        ReflectionTestUtils.setField(testTask, "id", id);

        Mockito.when(taskRepository.findNextTask(typeParam))
                .thenReturn(Optional.of(testTask));

        Mockito.when(taskRepository.claimTask(id))
                .thenReturn(1);

        Mockito.when(taskRepository.findById(id))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.pullAndClaimTask(typeParam))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Pending task discovered, locked, and claimed, but not found.");
    }

    @Test
    public void shouldReportSuccessfulTaskOutcome() {
        Long id = 3L;
        UUID taskUuid = UUID.randomUUID();

        Task testTask =  Task.createTask("background-job", Map.of());
        ReflectionTestUtils.setField(testTask, "id", id);
        ReflectionTestUtils.setField(testTask, "claimToken", taskUuid);
        ReflectionTestUtils.setField(testTask, "status", TaskStatus.RUNNING);

        TaskReportDto taskReport = TaskReportDto.builder()
                .outcome(TaskReportDto.Outcome.SUCCESS)
                .claimToken(taskUuid)
                .build();

        Mockito.when(taskRepository.findById(id))
                .thenReturn(Optional.of(testTask));

        taskService.reportTaskOutcome(id, taskReport);

        assertThat(testTask.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(testTask.getCompletedAt()).isNotNull();
        assertThat(testTask.getClaimToken()).isNull();

        Mockito.verify(taskRepository).save(testTask);
    }

    @Test
    public void shouldReportFailedTaskOutcomeAndReturnToPendingState() {
        Long id = 3L;
        UUID taskUuid = UUID.randomUUID();

        Task testTask =  Task.createTask("background-job", Map.of());
        ReflectionTestUtils.setField(testTask, "id", id);
        ReflectionTestUtils.setField(testTask, "claimToken", taskUuid);
        ReflectionTestUtils.setField(testTask, "status", TaskStatus.RUNNING);

        TaskReportDto taskReport = TaskReportDto.builder()
                .outcome(TaskReportDto.Outcome.FAILURE)
                .claimToken(taskUuid)
                .build();

        Mockito.when(taskRepository.findById(id))
                .thenReturn(Optional.of(testTask));

        taskService.reportTaskOutcome(id, taskReport);

        assertThat(testTask.getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(testTask.getNextRetryAt()).isNotNull();
        assertThat(testTask.getClaimToken()).isNull();

        Mockito.verify(taskRepository).save(testTask);
    }

    @Test
    public void shouldReportFailedTaskOutcomeAndReturnToDeadState() {
        Long id = 3L;
        UUID taskUuid = UUID.randomUUID();

        Task testTask =  Task.createTask("background-job", Map.of());
        ReflectionTestUtils.setField(testTask, "id", id);
        ReflectionTestUtils.setField(testTask, "claimToken", taskUuid);
        ReflectionTestUtils.setField(testTask, "status", TaskStatus.RUNNING);
        ReflectionTestUtils.setField(testTask, "failureCount", 3);

        TaskReportDto taskReport = TaskReportDto.builder()
                .outcome(TaskReportDto.Outcome.FAILURE)
                .claimToken(taskUuid)
                .build();

        Mockito.when(taskRepository.findById(id))
                .thenReturn(Optional.of(testTask));

        taskService.reportTaskOutcome(id, taskReport);

        assertThat(testTask.getStatus()).isEqualTo(TaskStatus.DEAD);
        assertThat(testTask.getNextRetryAt()).isNull();
        assertThat(testTask.getClaimToken()).isNull();


        Mockito.verify(taskRepository).save(testTask);
    }

    @Test
    public void shouldThrowTaskNotRunningExceptionWhenReportingTaskOutcome() {
        Long id = 3L;
        UUID taskUuid = UUID.randomUUID();

        Task testTask =  Task.createTask("background-job", Map.of());
        ReflectionTestUtils.setField(testTask, "id", id);
        ReflectionTestUtils.setField(testTask, "claimToken", taskUuid);

        TaskReportDto taskReport = TaskReportDto.builder()
                .outcome(TaskReportDto.Outcome.SUCCESS)
                .claimToken(taskUuid)
                .build();

        Mockito.when(taskRepository.findById(id))
                .thenReturn(Optional.of(testTask));

        assertThatThrownBy(() -> taskService.reportTaskOutcome(id, taskReport))
                .isInstanceOf(TaskNotRunningException.class)
                .hasMessage("Attempt to report execution outcome on task with id: " + id + ", but said task is " + testTask.getStatus());

        Mockito.verify(taskRepository, Mockito.never()).save(testTask);
    }

    @Test
    public void shouldThrowInvalidTaskClaimTokenExceptionWhenReportingTaskOutcome() {
        Long id = 3L;
        UUID taskUuid = UUID.randomUUID();

        Task testTask =  Task.createTask("background-job", Map.of());
        ReflectionTestUtils.setField(testTask, "id", id);
        ReflectionTestUtils.setField(testTask, "status", TaskStatus.RUNNING);
        ReflectionTestUtils.setField(testTask, "claimToken", taskUuid);

        UUID reportUuid = UUID.randomUUID();
        TaskReportDto taskReport = TaskReportDto.builder()
                .outcome(TaskReportDto.Outcome.SUCCESS)
                .claimToken(reportUuid)
                .build();

        Mockito.when(taskRepository.findById(id))
                .thenReturn(Optional.of(testTask));

        assertThatThrownBy(() -> taskService.reportTaskOutcome(id, taskReport))
                .isInstanceOf(InvalidTaskClaimTokenException.class)
                .hasMessage("Invalid task claim token: " + reportUuid + " for task with id: " + id);

        Mockito.verify(taskRepository, Mockito.never()).save(testTask);
    }
}
