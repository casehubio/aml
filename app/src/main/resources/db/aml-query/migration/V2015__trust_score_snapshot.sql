CREATE TABLE trust_score_snapshot (
    id                  UUID                     NOT NULL,
    agent_id            VARCHAR(128)             NOT NULL,
    capability          VARCHAR(100)             NOT NULL,
    alpha               DOUBLE PRECISION         NOT NULL,
    beta                DOUBLE PRECISION         NOT NULL,
    score               DOUBLE PRECISION         NOT NULL,
    snapshot_timestamp  TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_trust_score_snapshot PRIMARY KEY (id)
);

CREATE INDEX idx_trust_score_snapshot_agent_cap
    ON trust_score_snapshot (agent_id, capability, snapshot_timestamp);
