package com.jjetta.task_queue;

import com.jjetta.task_queue.model.Task;
import com.jjetta.task_queue.model.TaskStatus;
import com.jjetta.task_queue.model.TaskType;
import org.junit.jupiter.api.Test;

import static com.jjetta.task_queue.model.Task.createTask;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

public class TaskTest {

    @Test
    void createTaskWithNullType() {
        assertThatThrownBy(() -> {
            createTask(null, "payload");
        })
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Task type cannot be null");
    }

    @Test
    void createTaskWithNullPayload() {
        assertThatThrownBy(() -> {
            createTask(TaskType.SLEEP_SUCCEED, null);
        })
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Payload cannot be null");
    }

    @Test
    void createTaskWithBlankPayload() {
        assertThatThrownBy(() -> {
            createTask(TaskType.SLEEP_SUCCEED, "");
        })
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Payload cannot be blank");
    }

    @Test
    void validTransition() {
        Task task = createTask(TaskType.SLEEP_SUCCEED, "payload");
        task.transitionTo(TaskStatus.RUNNING);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.RUNNING);
    }

    @Test
    void invalidTransition() {
        Task task = createTask(TaskType.SLEEP_SUCCEED, "payload");
        TaskStatus expectedStatus = task.getStatus();
        assertThatThrownBy(() -> {
            task.transitionTo(TaskStatus.COMPLETED);
        })
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot transition from " + expectedStatus + " to " + TaskStatus.COMPLETED);
        assertThat(task.getStatus()).isEqualTo(expectedStatus);
    }

    @Test
    void replay() {}
}
