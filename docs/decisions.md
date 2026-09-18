# Architecture Decisions

A running log of decisions with alternatives. Context, decision, alternatives considered, and consequences included. 

---

## 1. Postgres as the queue

**Context:** need a durable, discoverable store for tasks, with correctness under concurrent claims.

**Decision:** Postgres is both the system of record and the queue itself — no separate broker or intermediate structure.

**Alternatives considered:**
- *In-memory queue* — rejected outright: anything unpicked is lost on crash/restart, and it doesn't support multiple 
- app instances sharing one queue.
- *Redis (Streams + consumer groups)* — a real contender, but doesn't give exactly-once claiming for free (a retry 
can re-add a task while an older unacked entry for it is still pending reclaim; closing that gap means rebuilding the 
same single-writer atomicity Postgres already provides). Also a weaker durability guarantee than a committed Postgres 
transaction — Redis's durability depends on AOF/RDB configuration, generally treated as "durable enough for a cache," 
not as a system of record.

**Consequences:** a partial index on `(type, next_retry_at) WHERE status='PENDING'` plus 
`SELECT ... FOR UPDATE SKIP LOCKED` keep this performant without a broker. Introducing Redis as the queue would create
a second, partially-overlapping source of truth for "is this task queued," adding consistency risk without removing the 
correctness work above.

---

## 2. Pull vs. push work distribution

**Context:** executors are arbitrary, unknown, possibly-personal processes in any language.

**Decision:** executors pull work by calling the system; the system never calls out to an executor-registered endpoint.

**Alternatives considered:**
- *Push* — would require the system to reach an executor's endpoint, which doesn't hold for NAT'd or personal machines.

**Consequences:** executors must be persistent, long-running polling processes — there's no ephemeral, 
zero-standing-infrastructure execution available in this model. Per-language SDKs (`@app.task`, `.delay()`) can make 
the network call feel nicer later, but can't remove it.

---

## 3. Transaction boundaries: claim, execution, completion kept separate

**Context:** a task's lifecycle spans claim, external execution, and completion reporting.

**Decision:** three separate, short-lived transactions, never one spanning the whole lifecycle.

**Alternatives considered:**
- *One transaction across the whole lifecycle* — would hold the row lock for the entire external execution time, and a
crash mid-execution would roll back the claim itself, silently reverting to `PENDING` and destroying the signal the
hang-detector depends on.

**Consequences:** an abandoned task instead leaves a visibly `RUNNING` row with a real `claimed_at` — exactly what the
hang-detector (§ decision 5) needs to act on.

---

## 4. Age-based eviction instead of liveness detection

**Context:** a `type` with no real executor behind it (typo, or an executor that's permanently gone) leaves tasks 
`PENDING` forever with nothing surfacing that fact.

**Decision:** evict based on how long a task has sat `PENDING` (via `createdAt`), rather than tracking executor 
liveness.

**Alternatives considered:**
- *Executor heartbeat/TTL, or a `type` registry validated at submission* — out of scope for now: pull already requires 
a persistent polling process regardless, so proving liveness up front isn't solving a problem age-based eviction 
doesn't already cover adequately.

**Consequences:** revisit heartbeat/registry only if age-based eviction proves insufficient once the system is running.

---

## 5. Hung-task detection kept separate from eviction

**Context:** an executor can claim a task and never report back (crash, lost network, killed, buggy) — a different 
failure mode from a task that was never claimed at all.

**Decision:** a separate scheduled check for rows stuck in `RUNNING` past `claimed_at + timeout`, distinct from 
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

**Context:** two different spots need to guard against acting on a stale row: the claim 
(many executors racing for `PENDING` work) and the hung-task sweeper 
(racing against a real completion report that lands mid-sweep).

**Decision:** the claim uses a pessimistic conditional `UPDATE ... WHERE status = 'PENDING'`; the sweeper uses 
optimistic locking via `@Version`.

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

## 7. Authentication/authorization deferred

**Context:** every endpoint is currently open. any caller can create tasks, pull and claim work of any `type`, report 
outcomes, and view or replay the dead-letter queue. I built this project envisioning there's a single integrating dev/team, 
but at runtime their system (should) show up as multiple distinct callers hitting this API: their application code 
enqueueing work (producer), their worker processes claiming and reporting on it (executor), and the dev/an admin script 
managing the DLQ (operator). Each is a different network principal.

**Decision:** ship without authn/authz for now. This is a deliberate scope cut, not just overlooking it. The actor 
model above needs to be settled before picking a mechanism, and the system isn't being exposed beyond a trusted 
environment yet.

**Alternatives considered:**
- *Build it now* — rejected for now: whether executors should be restricted to specific `type`s, and how 
fine-grained operator permissions need to be, aren't settled yet; don't wanna risk building the wrong thing by 
picking a mechanism before that's clear
- *Full OAuth2/user-login flow* — Callers in the context of this project are expected to be services 
(a web app, a worker fleet, an admin script), not people, so a simpler per-caller identity mapped to a role 
(producer/executor/operator) is probably sufficient once this is tackled.

**Consequences:** must be resolved before any deployment outside a fully trusted network. The `claimToken` issued at 
claim time is a per-task capability (proves *this* caller won *this specific* claim) — it is not a substitute for 
authenticating who's allowed to call the API at all, and it protects nothing on endpoints it isn't checked against.
