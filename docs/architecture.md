
# Architecture
This document is a description of the intended architecture of the system. 

### Execution Model
Tasks types are open world, meaning that they are user defined and arbitrary. What this means is that the system 
doesn't actually execute tasks, but simply stores them instead. Users execute run their own executor process, 
in any language. The system doesn't care, and is only responsible for: 
- durable storage
- task claiming
- task retry, backoff, and DLQ bookkeeping
- routing

Client executors most be persistent, long-running polling process since the system runs on a pull-based model.
(ADR: pull vs. push)

### Postgres as the Queue
There is no separate queueing structure/mechanism except the database itself. Queue-like 
behavior can be achieved via Postgres primitives like row-level locking. A row that has a status of `PENDING` is itself 
what signals that a task is "queued" for execution. Same thing goes for task discovery and claiming, where a partial 
index on `(type, next_retry_at)` `WHERE status = 'PENDING'` remains relatively small. 

### Concurrency Safety: The Atomic Claim
Here's where the magic happens. Discovery and claiming happen in a single query:

```sql
UPDATE tasks
SET status = 'RUNNING',
    claimed_at = now(),
    claim_token = gen_random_uuid()
WHERE id = (
    SELECT id FROM tasks
    WHERE status = 'PENDING'
        AND type = :type
        AND (next_retry_at <= now() OR next_retry_at IS NULL)
    ORDER BY COALESCE(next_retry_at, created_at)
    FOR UPDATE SKIP LOCKED
    LIMIT 1
)
RETURNING *
```

The subquery does the finding: `SKIP LOCKED` skips rows another concurrent claim already has locked, and 
`FOR UPDATE` locks the one row it picks. The outer `UPDATE` does the claiming against that exact row, and 
`RETURNING *` hands back the claimed row in the same round trip — no separate fetch needed afterward.

Two concurrent claims can never pick the same row: `FOR UPDATE SKIP LOCKED` guarantees each caller's subquery 
lands on a row no other in-flight claim holds a lock on.

### State Machine
Tasks can be in one of four states:
- `PENDING`
- `RUNNING`
- `COMPLETED`
- `DEAD`
<img width="2501" height="1186" alt="Screenshot 2026-09-22 at 8 42 14 PM" src="https://github.com/user-attachments/assets/917da36d-2825-4535-8ed7-6b22c67aae1c" />
`FAILED` is never persisted as a state on a task, as failure simply results in incrementing a task's failure count 
by one:
- If the failure count is less than `maxRetries`, the state moves back to `PENDING`
- If the failure count is greater than `maxRetries`, the task is moved to the DLQ (dead-letter-queue, or simply, a `DEAD` state)

### Age-Based Eviction
A `PENDING` task that has never been claimed (`failureCount = 0`) and has sat past the configured age threshold 
(measured from `createdAt`) gets evicted (sent straight to the DLQ). This is scoped to never-claimed tasks 
specifically: once a task has failed at least once, an executor clearly exists for its `type`, so the eviction 
sweep leaves it to the ordinary retry/backoff cycle instead. This catches typos in task type names, or dead types 
that will never be executed. (ADR: eviction vs. liveness detection)

### Hung/Abandoned Task Detection
A scheduled check finds rows stuck in `RUNNING` past `claimed_at + timeout` and treats them as failed/orphaned.
This is the only recovery mechanism needed, since execution never runs on this system's own threads 
(ADR: eviction and hang-detection as separate mechanisms).

### Task Entity
| Field          | Purpose                                                                                     |
|----------------|---------------------------------------------------------------------------------------------|
| `id`           | primary key                                                                                 |
| `version`      | `@Version`; guards the sweeper's write against a task that was reported on in the meantime  |
| `type`         | open string; routing label                                                                  |
| `params`       | `JSONB`; executor parameters                                                                |
| `status`       | state machine state                                                                         |
| `failureCount` | retry/DLQ threshold tracking                                                                |
| `createdAt`    | eviction timing; start of end-to-end latency                                                |
| `claimedAt`    | hang-detector's only signal once claimed                                                    |
| `completedAt`  | end of end-to-end latency                                                                   |
| `nextRetryAt`  | backoff timing, checked at pull-time                                                        |
| `claimToken`   | UUID identifying which executor may report on this task                                     |

Two different locking strategies guard against stale writes, used in different places for different reasons *(ADR: pessimistic claim vs. optimistic sweeper guard)*:
- **Pessimistic** — the claim's single `UPDATE ... WHERE id = (SELECT ... FOR UPDATE SKIP LOCKED)` (§ Concurrency Safety above).
- **Optimistic** — `@Version` on the sweeper's write, since the sweeper reuses `Task.recordFailure()`'s existing Java branching/backoff logic rather than reimplementing it as raw SQL.

### Observability
On top of what Spring Boot Actuator gives you for free, I wired up domain specific metrics with Micrometer:
- Queue depth - Gauge, simple count of `PENDING` rows
- Task latency - (`claimedAt -> completedAt`)
- Error rate - a tagged Counter (`tasks.processed`, tagged `outcome=success/failure`)
