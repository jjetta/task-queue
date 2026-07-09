package com.jjetta.task_queue.repository;

import com.jjetta.task_queue.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, Long> {
}
