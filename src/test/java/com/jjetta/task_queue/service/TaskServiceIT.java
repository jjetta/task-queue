package com.jjetta.task_queue.service;

import com.jjetta.task_queue.model.Task;
import com.jjetta.task_queue.repository.TaskRepository;
import com.jjetta.task_queue.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

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
    public void testPullAndClaimTaskConcurrently() throws Exception {
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
}
