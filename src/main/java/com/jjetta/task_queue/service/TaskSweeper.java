package com.jjetta.task_queue.service;

import com.jjetta.task_queue.config.SweeperProperties;
import com.jjetta.task_queue.model.Task;
import com.jjetta.task_queue.repository.TaskRepository;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@Slf4j
public class TaskSweeper {

    private final TaskRepository taskRepository;
    private final SweeperProperties sweeperProperties;
    private final TaskSweeperService taskSweeperService;
    private final Logger logger = LoggerFactory.getLogger(TaskSweeper.class);

    public TaskSweeper(TaskRepository taskRepository,  SweeperProperties sweeperProperties, TaskSweeperService taskSweeperService) {
        this.taskRepository = taskRepository;
        this.sweeperProperties = sweeperProperties;
        this.taskSweeperService = taskSweeperService;
    }

    @Scheduled(fixedDelayString = "${app.sweeper.interval}")
    public void timeoutStaleTasks() {
        Instant cutoff = Instant.now().minus(sweeperProperties.taskTimeout());
        List<Task> staleTasks = taskRepository.findStaleRunningTasks(cutoff);
        for  (Task task : staleTasks) {
            try {
                taskSweeperService.timeoutStaleTask(task);
            } catch (ObjectOptimisticLockingFailureException e) {
                logger.warn("Sweeper no longer timing out task {}. Ran into exception: {}", task.getId(), e.getMessage());
            }
        }
    }

}
