package com.jjetta.task_queue;

import com.jjetta.task_queue.model.Task;
import com.jjetta.task_queue.model.TaskType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

public class TaskTest {

    @Test
    void createTaskWithNullType() {
        assertThatThrownBy(() -> {
            Task.createTask(null, "payload");
        })
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Task type cannot be null");
    }

    @Test
    void createTaskWithNullPayload() {
        assertThatThrownBy(() -> {
            Task.createTask(TaskType.SLEEP_SUCCEED, null);
        })
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Payload cannot be null");
    }

    @Test
    void createTaskWithBlankPayload() {
        assertThatThrownBy(() -> {
            Task.createTask(TaskType.SLEEP_SUCCEED, "");
        })
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Payload cannot be blank");
    }

    @Test
    void transitionTo() {}

    @Test
    void replay() {}
}
