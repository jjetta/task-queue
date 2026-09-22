# Architecture Decisions

A running log of decisions with alternatives.

---

## 1. Postgres as the queue

**Context:** We need a durable, discoverable store for tasks, with correctness under concurrent claims.

**Decision:** Postgres can serve as both the system of record and the queue itself, with no need for separate broker 
or intermediate structure.

**Alternatives considered:**
- *In-memory queue* — rejected: Any unclaimed task is lost on crashes or restarts. It also doesn't support multiple 
app instances sharing one queue.
- *Redis (Streams + consumer groups)* — A real contender, but doesn't give exactly-once claiming for free
We'd have to rebuild the same single-writer atomicity Postgres already provides from scratch). Also, a weaker 
durability guarantee than a committed Postgres transaction. Redis's durability depends on AOF/RDB configuration,
which may be durable enough for a cache, but not as a system of record.

**Consequences:** a partial index on `(type, next_retry_at) WHERE status='PENDING'` plus 
`SELECT ... FOR UPDATE SKIP LOCKED` keep this performant without a broker. Introducing Redis as the queue would create
a second, partially-overlapping source of truth for "is this task queued," adding consistency risk without removing the 
correctness work above.

---

## 2. Pull vs. push work distribution

**Context:** Client executors are arbitrary, unknown, processes in that could be in any language.

**Decision:** Executors pull work by calling the system; the system never calls out to an executor-registered endpoint.

**Alternatives considered:**
- *Push* — This would require the system to reach an executor's endpoint, which doesn't hold for NAT'd or personal machines.

**Consequences:** Executors must be persistent, long-running polling processes. There's no ephemeral execution 
available in this model. Per-language SDKs maybe could make this easier to implement this for users maybe?
But there's no way around the network call.

---

## 3. Transaction boundaries: claim, execution, completion kept separate

**Context:** A task's lifecycle spans claim, external execution, and completion reporting.

**Decision:** Three separate, short-lived transactions, never one spanning the whole lifecycle.

**Alternatives considered:**
- *One transaction across the whole lifecycle*: This would hold the row lock for the entire external execution time, 
and a crash mid-execution would roll back the claim itself, silently reverting to `PENDING` and destroying the signal the
hang-detector depends on.

**Consequences:** an abandoned task instead leaves a visibly `RUNNING` row with a real `claimed_at`, exactly what the
hang-detector (§ decision 5) needs to act on.

---

## 4. Age-based eviction instead of liveness detection

**Context:** A `type` with no real executor behind it (typo, or an executor that's permanently gone) leaves tasks 
`PENDING` forever.

**Decision:** Evict based on how long a task has sat `PENDING` (via `createdAt`), rather than tracking executor 
liveness.

**Alternatives considered:**
- *Executor heartbeat/TTL* — out of scope for now: pull already requires a persistent polling process regardless,
so proving liveness up front isn't solving a problem age-based eviction doesn't already cover adequately.

**Consequences:** revisit heartbeat/registry only if age-based eviction proves insufficient once the system is running.
---

## 5. Hung-task detection kept separate from eviction

**Context:** An executor can claim a task and never report back (crash, lost network, killed, buggy), which is  different 
from a task that was never claimed at all.

**Decision:** A separate scheduled check for rows stuck in `RUNNING` past `claimed_at + timeout`, distinct from 
age-based eviction of never-claimed `PENDING` rows.

**Alternatives considered:**
- *A single mechanism covering both* — rejected because the two failure modes have different signals 
- (`createdAt` vs. `claimedAt`) and different meanings 
- (no executor exists for this type, vs. an executor took work and disappeared).

**Consequences:** this is the only recovery mechanism needed on the abandoned-claim side,
since there's no worker thread on this system's side to reclaim. All execution happens in the external executor's 
process.

---

## 6. Pessimistic locking for the claim, optimistic locking for the sweeper

**Context:** Two different spots need to guard against acting on a stale row: the claim 
(many executors racing for `PENDING` work) and the hung-task sweeper 
(racing against a real completion report that lands mid-sweep).

**Decision:** the claim uses a pessimistic `UPDATE ... WHERE id = (SELECT ... FOR UPDATE SKIP LOCKED)`; the sweeper 
uses optimistic locking via `@Version`.

**Alternatives considered:**
- *A conditional `UPDATE ... WHERE status = 'RUNNING' AND claimed_at < ?` for the sweeper, matching the claim's 
pattern* — would work, but the sweeper needs to reuse `Task.recordFailure()`'s existing branching/backoff logic 
in Java (retry vs. `DEAD`, backoff calculation) rather than reimplementing that logic as raw SQL. 
- `@Version` gives a generic "did this row change since I read it" guard on `save()` for free, without hand-writing 
that `WHERE` clause or duplicating the branching logic in SQL.

**Consequences:** losing the race to a real completion report throws `ObjectOptimisticLockingFailureException`, 
caught per-task in the sweeper's loop and logged at WARN — expected, correct behavior, not a bug. Each task is swept 
in its own `@Transactional` method (not one transaction for the whole batch), so one conflict doesn't affect 
any other task in the same run.

---

## 7. Standard JRE container image over GraalVM Native Image

**Context:** the app is being containerized so `docker-compose.yml` can reference a published image instead of a 
local build. GraalVM Native Image was considered for that image, mainly for faster cold start and a smaller runtime 
footprint.

**Decision:** ship a standard JRE-based image for now, not a Native Image build.

**Alternatives considered:**
- *GraalVM Native Image* — rejected (for now): I was thinking of going with GraalVM after looking it up, and it seems pretty
cool. But I'm not sure if it's necessary for this project's needs. Fast cold starts mainly pay off for elastic 
autoscaling deployments, where instances start and stop frequently. This system would hypothetically run long-lived, 
so startup cost is rarely paid (on deploy, or crash-restart). On top of that, Native Image poses integration risk due to it not 
necessarily pairing well with Java reflection features. 

**Consequences:** revisit if the horizontal scale-out work (§ roadmap) moves toward elastic autoscaling. That's 
the point where instances starting frequently in response to load would make fast cold start a real operational 
need instead of a theoretical one, and the trade would need re-evaluating with that concrete need in hand.
