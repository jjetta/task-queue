# Distributed Task Queue

A durable, Postgres-backed task queue for asynchronous work. Any language, any process: producers enqueue tasks over HTTP, 
executors pull and run them however they like, and the system handles durability, claiming, retries, and dead-letter bookkeeping.
All without a broker.

Most non-obvious design choices in this repo are written down, with the alternatives that were rejected and why. See [`docs/decisions.md`](docs/decisions.md).

## Why Postgres and not a broker?

Because a committed transaction is a stronger durability guarantee than most message brokers give you for free,
and `SELECT ... FOR UPDATE SKIP LOCKED` already gives concurrent claimers exactly the atomicity a broker would 
otherwise need to be introduced to provide. One system of record, one place correctness lives.
The full reasoning can be found in [decision #1](docs/decisions.md#1-postgres-as-the-queue).

## Design highlights

- **Atomic claiming** — an indexed `SELECT ... FOR UPDATE SKIP LOCKED` followed by a conditional 
`UPDATE ... WHERE status = 'PENDING'` means concurrent executors never race for the same task.
- **Two failure modes, two mechanisms** — a task nobody ever picks up (no executor for its `type`) and a task an 
executor claimed and then vanished on are different problems with different signals (`createdAt` vs `claimedAt`),
detected separately via age-based eviction and a hung-task sweeper.
- **Exponential backoff with jitter, then the DLQ** — failures retry with configurable backoff up to a max retry count,
then move to a `DEAD` state for manual inspection and replay.
- **Short-lived transactions on purpose** — claim, execution, and completion-reporting are three separate transactions,
never one spanning the whole task lifecycle, so a crash mid-execution can't silently roll back the claim itself.
- **Two locking strategies, deliberately different** — a pessimistic conditional update for the claim path, optimistic 
`@Version` locking for the sweeper ([decision #6](docs/decisions.md#6-pessimistic-locking-for-the-claim-optimistic-locking-for-the-sweeper)).
- **RFC 9457 error responses** — every failure mode returns a structured `ProblemDetail`, not an ad hoc error shape.
- **Metrics that mean something** — queue depth, claim-to-completion latency, success/failure counts via Micrometer/Prometheus.

## Tech stack

Java 21 · Spring Boot 4 (Web MVC, Data JPA, Validation, Actuator) · PostgreSQL · Flyway · Micrometer/Prometheus · springdoc-openapi · JUnit 5 + Testcontainers

## Getting started

```bash
git clone https://github.com/jjetta/task-queue.git
cd task-queue
docker compose up -d      # starts Postgres on localhost:5433
./mvnw spring-boot:run    # Flyway migrates the schema automatically on boot
```

- API served on `localhost:8080`.
- Interactive OpenAPI docs at `/swagger-ui.html`.
- Actuator health/metrics at `/actuator/health`, `/actuator/metrics`, `/actuator/prometheus`.

## API surface

| Method & path | Purpose |
|---|---|
| `POST /v1/tasks` | Enqueue a task (`type` + arbitrary `params`) |
| `GET /v1/tasks/{id}` | Look up a task by ID |
| `GET /v1/tasks/next?type=` | Executor pulls and atomically claims the next task |
| `POST /v1/tasks/{id}/report` | Executor reports success/failure with its claim token |
| `GET /v1/tasks/dead` | List tasks that exhausted retries (the DLQ) |
| `POST /v1/tasks/{id}/replay` | Reset a dead task back to `PENDING` |

- Behavior and rationale for each endpoint: [`docs/api.md`](docs/api.md).
- Exact, always-current request/response schemas: [Swagger UI](http://localhost:8080/swagger-ui.html) (`/v3/api-docs` for raw OpenAPI).
- System design and the task state machine: [`docs/architecture.md`](docs/architecture.md).

## Testing

Unit tests and Testcontainers-backed integration tests (real Postgres, real transactional/locking behavior) run as separate CI jobs.

```bash
./mvnw test -Dtest='!*IT'   # unit tests
./mvnw test -Dtest='*IT'    # integration tests (requires Docker)
```

## Status & roadmap

Single-node, correctness-first today: one Postgres instance as the system of record, safely shared by any number of producer/executor processes. Not yet authenticated, not yet horizontally scaled or partitioned, and not yet runnable without a local clone — all deliberately scoped, not overlooked:

- **Auth/authz** — every endpoint is currently open. See [decision #7](docs/decisions.md#7-authenticationauthorization-deferred) for the producer/executor/operator actor model this is designed around.
- **Horizontal scale-out** — claiming is already safe across multiple app instances sharing one Postgres; running and load-testing that, then pushing into partitioning/replication, is next.
- **Publish a container image** — package the app itself as a Docker image and publish it via CI, so `docker-compose.yml` can reference the published image instead of a local build. At that point the whole stack (app + Postgres) runs from a single compose file, with no clone, Java, or Maven required.

## License

MIT — see [`LICENSE`](LICENSE).
