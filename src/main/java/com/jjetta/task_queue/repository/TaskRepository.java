package com.jjetta.task_queue.repository;

import com.jjetta.task_queue.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {

    @Query(value = """
            SELECT * FROM tasks
            WHERE status = 'PENDING'
                AND type = :type
                AND (next_retry_at <= now() OR next_retry_at IS NULL)
            ORDER BY COALESCE(next_retry_at, created_at)
            FOR UPDATE SKIP LOCKED
            LIMIT 1
            """, nativeQuery = true)
    Optional<Task> findNextTask(@Param("type") String type);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE tasks
            SET status = 'RUNNING',
                claimed_at = now(),
                claim_token = gen_random_uuid()
            WHERE id = :id
                AND status = 'PENDING'
            """, nativeQuery = true)
    int claimTask(@Param("id") Long id);

    @Query(value = """
            SELECT * FROM tasks
            WHERE status = 'RUNNING'
                AND claimed_at < :cutoff
            """, nativeQuery = true)
    List<Task> findStaleRunningTasks(@Param("cutoff") Instant cutoff);
}
