package com.jjetta.task_queue.controller;

import com.jjetta.task_queue.exception.TaskNotFoundException;
import com.jjetta.task_queue.model.Task;
import com.jjetta.task_queue.service.TaskService;
import com.jjetta.task_queue.web.TaskCreationRequestDto;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
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

@WebMvcTest(TaskController.class)
public class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    private TaskService taskService;

    @Test
    public void shouldCreateTask() throws Exception {
        TaskCreationRequestDto requestDto = TaskCreationRequestDto.builder()
                .type("background-job")
                .params(Map.of())
                .build();

        Task testTask = Task.createTask("background-job", Map.of());
        ReflectionTestUtils.setField(testTask, "id", 1L);

        Mockito.when(taskService.createTask(Mockito.any(String.class), Mockito.any(Map.class)))
                .thenReturn(testTask);

        String json = objectMapper.writeValueAsString(requestDto);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/tasks")
                .content(json)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(MockMvcResultMatchers.header().string(
                        "Location", Matchers.endsWith("/api/v1/tasks/1")
                ))
                .andExpect(MockMvcResultMatchers.jsonPath("$.id").value(1));

        Mockito.verify(taskService).createTask(Mockito.any(String.class), Mockito.any(Map.class));
    }

    @Test
    public void should400OnInvalidRequest() throws Exception {
        TaskCreationRequestDto requestDto = TaskCreationRequestDto.builder()
                .type(null)
                .params(Map.of())
                .build();

        String json = objectMapper.writeValueAsString(requestDto);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/tasks")
                .content(json)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());

        Mockito.verifyNoInteractions(taskService);
    }

    @Test
    public void shouldGetTask() throws Exception {
        Long id = 1L;

        Task testTask = Task.createTask("background-job", Map.of());
        ReflectionTestUtils.setField(testTask, "id", 1L);

        Mockito.when(taskService.getTaskById(Mockito.anyLong()))
                .thenReturn(testTask);

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/tasks/{id}", id))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.id").value(testTask.getId()));

        Mockito.verify(taskService).getTaskById(Mockito.anyLong());

    }

    @Test
    public void should404OnTaskNotFound() throws Exception {
        Long id = 1L;

        TaskNotFoundException ex = new TaskNotFoundException(id);
        Mockito.when(taskService.getTaskById(Mockito.anyLong())).thenThrow(ex);

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/tasks/{id}", id))
                .andDo(MockMvcResultHandlers.print())
                .andExpect(MockMvcResultMatchers.status().isNotFound());

        Mockito.verify(taskService).getTaskById(Mockito.anyLong());
    }
}
