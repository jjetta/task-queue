CREATE TYPE task_status AS ENUM ('PENDING', 'RUNNING', 'COMPLETED', 'DEAD');

CREATE TABLE tasks (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY ,
    type varchar(255) NOT NULL,
    params jsonb,
    status task_status NOT NULL,
    failure_count int NOT NULL,
    created_at timestamp DEFAULT now() NOT NULL,
    claimed_at timestamp,
    completed_at timestamp,
    next_retry_at timestamp
);