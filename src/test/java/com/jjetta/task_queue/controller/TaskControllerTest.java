package com.jjetta.task_queue.controller;

import com.jjetta.task_queue.exception.TaskNotFoundException;
import com.jjetta.task_queue.model.Task;
import com.jjetta.task_queue.service.TaskService;
import com.jjetta.task_queue.web.TaskCreationRequestDto;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Optional;

@WebMvcTest(TaskController.class)
public class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    private TaskService taskService;

    @Test
    public void shouldCreateTaskSuccessfully() throws Exception {
        TaskCreationRequestDto requestDto = TaskCreationRequestDto.builder()
                .type("background-job")
                .params(Map.of())
                .build();

        Task testTask = Task.createTask("background-job", Map.of());
        ReflectionTestUtils.setField(testTask, "id", 1L);

        Mockito.when(taskService.createTask(Mockito.any(String.class), Mockito.any(Map.class)))
                .thenReturn(testTask);

        String json = objectMapper.writeValueAsString(requestDto);

        mockMvc.perform(MockMvcRequestBuilders.post("/v1/tasks")
                .content(json)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(MockMvcResultMatchers.header().string(
                        "Location", Matchers.endsWith("/v1/tasks/1")
                ))
                .andExpect(MockMvcResultMatchers.jsonPath("$.id").value(1));

        Mockito.verify(taskService).createTask(Mockito.any(String.class), Mockito.any(Map.class));
    }

    @Test
    public void should400OnInvalidTaskCreationRequest() throws Exception {
        TaskCreationRequestDto requestDto = TaskCreationRequestDto.builder()
                .type(null)
                .params(Map.of())
                .build();

        String json = objectMapper.writeValueAsString(requestDto);

        mockMvc.perform(MockMvcRequestBuilders.post("/v1/tasks")
                .content(json)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());

        Mockito.verifyNoInteractions(taskService);
    }

    @Test
    public void shouldGetTaskSuccessfully() throws Exception {
        Long id = 1L;

        Task testTask = Task.createTask("background-job", Map.of());
        ReflectionTestUtils.setField(testTask, "id", 1L);

        Mockito.when(taskService.getTaskById(id))
                .thenReturn(testTask);

        mockMvc.perform(MockMvcRequestBuilders.get("/v1/tasks/{id}", id))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.id").value(1L));

        Mockito.verify(taskService).getTaskById(Mockito.anyLong());

    }

    @Test
    public void should404OnTaskNotFoundWhenGettingTask() throws Exception {
        Long id = 1L;

        TaskNotFoundException ex = new TaskNotFoundException(id);
        Mockito.when(taskService.getTaskById(id)).thenThrow(ex);

        mockMvc.perform(MockMvcRequestBuilders.get("/v1/tasks/{id}", id))
                .andDo(MockMvcResultHandlers.print())
                .andExpect(MockMvcResultMatchers.status().isNotFound());

        Mockito.verify(taskService).getTaskById(Mockito.anyLong());
    }

    @Test
    public void shouldPullNextTaskSuccessfully() throws Exception {
        String typeParam = "background-job";

        Task testTask = Task.createTask(typeParam, Map.of());
        ReflectionTestUtils.setField(testTask, "id", 3L);

        Mockito.when(taskService.pullAndClaimTask(typeParam))
                .thenReturn(Optional.of(testTask));

        mockMvc.perform(MockMvcRequestBuilders.get("/v1/tasks/next?type={typeParam}", typeParam))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.id").value(3L))
                .andExpect(MockMvcResultMatchers.jsonPath("$.type").value(typeParam));

        Mockito.verify(taskService).pullAndClaimTask(typeParam);
    }

    @Test
    public void shouldReceiveNoContentWhenPullingNextTask() throws Exception {
        String typeParam = "background-job";

        Mockito.when(taskService.pullAndClaimTask(typeParam))
                .thenReturn(Optional.empty());

        mockMvc.perform(MockMvcRequestBuilders.get("/v1/tasks/next?type={typeParam}", typeParam))
                .andExpect(MockMvcResultMatchers.status().isNoContent());

        Mockito.verify(taskService).pullAndClaimTask(typeParam);
    }

}
