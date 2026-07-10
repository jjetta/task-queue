package com.jjetta.task_queue;

import com.jjetta.task_queue.model.TaskStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

public class TaskStatusTest {

    @ParameterizedTest
    @MethodSource("taskStatusAndBooleanProvider")
    void taskStatusTransition(TaskStatus from, TaskStatus to, boolean expected) {
        assertThat(from.canTransitionTo(to)).isEqualTo(expected);
    }

    static Stream<Arguments> taskStatusAndBooleanProvider() {
        return Stream.of(
                arguments(TaskStatus.PENDING,   TaskStatus.PENDING,   false),
                arguments(TaskStatus.PENDING,   TaskStatus.RUNNING,   true),
                arguments(TaskStatus.PENDING,   TaskStatus.COMPLETED, false),
                arguments(TaskStatus.PENDING,   TaskStatus.DEAD,      false),

                arguments(TaskStatus.RUNNING,   TaskStatus.PENDING,   true),
                arguments(TaskStatus.RUNNING,   TaskStatus.RUNNING,   false),
                arguments(TaskStatus.RUNNING,   TaskStatus.COMPLETED, true),
                arguments(TaskStatus.RUNNING,   TaskStatus.DEAD,      true),

                arguments(TaskStatus.COMPLETED, TaskStatus.PENDING,   false),
                arguments(TaskStatus.COMPLETED, TaskStatus.RUNNING,   false),
                arguments(TaskStatus.COMPLETED, TaskStatus.COMPLETED, false),
                arguments(TaskStatus.COMPLETED, TaskStatus.DEAD,      false),

                arguments(TaskStatus.DEAD,      TaskStatus.PENDING,   true),
                arguments(TaskStatus.DEAD,      TaskStatus.RUNNING,   false),
                arguments(TaskStatus.DEAD,      TaskStatus.COMPLETED, false),
                arguments(TaskStatus.DEAD,      TaskStatus.DEAD,      false)
        );
    }
}
