# HTTP API Surface

### POST /tasks - ingestion
1. Client `POST`s a new task.
2. Task row written to Postgres (with a default status of `PENDING`).
3. Task ID is returned to the client.

### GET /tasks/{id} - read/poll
- A straightforward Postgres lookup by ID, returning the full Task entity.

### GET /tasks/next?type=X - pull/claim
- An executor asks for a `PENDING` task matching the `type` it knows how to run. 
- Internally, this runs the indexed `SELECT ... FOR UPDATE SKIP LOCKED` query, followed by the atomic `UPDATE` claim.
- If an eligible task is present, said task's id, type, params, and claimToken are returned to the client.
- "No eligible task right now" is represented by a 204 NO CONTENT HTTP status.

### POST /tasks/{id}/report - result reporting
- The executor reports the outcome of the task (success or failure) via this endpoint.
- This is the system's only means of learning an execution's outcome, since execution happens entirely outside the system's process.
- Callers must provide the claimToken (a UUID), to verify that they were in fact the one who claimed and executed the task.
- If the claimToken doesn't match the task's, an InvalidTaskClaimTokenException is thrown, which is caught by the GlobalExceptionHandler, returning a 409 CONFLICT HTTP status.

### GET /tasks/dead - read/poll dead tasks
- A straightforward Postgres lookup, returning full Task entities that have a `DEAD` status.

### POST /tasks/{id}/replay - replay dead tasks
- Dead tasks are replayed manually via this endpoint.
- Internally:
    - failureCount is reset to zero.
    - createdAt is set to Instant.now()
    - nextRetryAt is nullified
