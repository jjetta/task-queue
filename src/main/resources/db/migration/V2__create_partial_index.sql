CREATE INDEX idx_pending_tasks
ON tasks (type, next_retry_at)
WHERE status = 'PENDING';