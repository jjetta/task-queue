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
    public void shouldCreateTask() {
        String type = "background-job";
        Map<String, Object> params = new HashMap<>();

        ArgumentCaptor<Task> taskArgumentCaptor = ArgumentCaptor.forClass(Task.class);

        Mockito.when(taskRepository.save(Mockito.any(Task.class))).thenAnswer(AdditionalAnswers.returnsFirstArg());

        Task testTask = taskService.createTask(type, params);

        Mockito.verify(taskRepository).save(taskArgumentCaptor.capture());

        Task createdTask = taskArgumentCaptor.getValue();

        assertThat(createdTask.getType()).isEqualTo(type);
        assertThat(createdTask.getParams()).isEqualTo(params);
        assertThat(createdTask).isSameAs(testTask);
    }

    @Test
    public void shouldGetTask() {
        Task existingTask = Task.createTask("background-job", Map.of());
        ReflectionTestUtils.setField(existingTask, "id", 1L);

        Mockito.when(taskRepository.findById(1L)).thenReturn(Optional.of(existingTask));

        Task foundTask = taskService.getTaskById(1L);

        assertThat(foundTask).isEqualTo(existingTask);
        Mockito.verify(taskRepository, Mockito.times(1)).findById(1L);
    }

    @Test
    public void shouldThrowTaskNotFoundException() {
        Long id = 1L;

        Mockito.when(taskRepository.findById(Mockito.anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.getTaskById(id))
                .isInstanceOf(TaskNotFoundException.class)
                .hasMessageContaining("Task with id " + id + " not found");

        Mockito.verify(taskRepository, Mockito.times(1)).findById(Mockito.anyLong());
    }
}
