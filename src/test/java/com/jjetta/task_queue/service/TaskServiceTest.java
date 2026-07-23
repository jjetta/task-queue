package com.jjetta.task_queue.service;

import com.jjetta.task_queue.exception.TaskNotFoundException;
import com.jjetta.task_queue.repository.TaskRepository;
import com.jjetta.task_queue.model.Task;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
public class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private TaskService taskService;

    private Task task;

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
}
