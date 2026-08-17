-- Consolidated initial schema for aml-engine-ledger.
-- Replaces V3000–V3012 incremental migrations (no production database exists).

CREATE TABLE case_ledger_entry (
    id           UUID         NOT NULL,
    case_id      UUID         NOT NULL,
    command_type VARCHAR(100),
    event_type   VARCHAR(100),
    case_status  VARCHAR(50),
    CONSTRAINT pk_case_ledger_entry PRIMARY KEY (id),
    CONSTRAINT fk_case_ledger_entry FOREIGN KEY (id) REFERENCES ledger_entry(id)
);

CREATE INDEX idx_cle_case_id ON case_ledger_entry (case_id);

CREATE TABLE worker_decision_entry (
    id                     UUID          NOT NULL,
    worker_id              VARCHAR(255)  NOT NULL,
    capability_tag         VARCHAR(255),
    case_id                UUID          NOT NULL,
    trust_score_at_routing DOUBLE PRECISION,
    threshold_applied      DOUBLE PRECISION,
    routing_rationale      TEXT,
    CONSTRAINT pk_worker_decision_entry PRIMARY KEY (id),
    CONSTRAINT fk_worker_decision_entry FOREIGN KEY (id) REFERENCES ledger_entry(id)
);

CREATE INDEX idx_wde_case_id    ON worker_decision_entry (case_id);
CREATE INDEX idx_wde_worker_id  ON worker_decision_entry (worker_id);
CREATE INDEX idx_wde_capability ON worker_decision_entry (capability_tag);

CREATE TABLE aml_case_profile_ledger_entry (
    id                   UUID           NOT NULL,
    flag_reason          VARCHAR(50)    NOT NULL,
    transaction_amount   DECIMAL(19,4)  NOT NULL,
    prior_incident_count INTEGER        NOT NULL,
    entity_type          VARCHAR(50),
    jurisdiction_risk    VARCHAR(50),
    network_complexity   VARCHAR(50),
    outcome              VARCHAR(50)    NOT NULL,
    confidence           DOUBLE PRECISION,
    investigation_path   VARCHAR(1000)  NOT NULL,
    narrative_seeded     BOOLEAN,
    seed_count           INTEGER,
    adaptation_method    VARCHAR(50),
    PRIMARY KEY (id),
    FOREIGN KEY (id) REFERENCES ledger_entry(id)
);

CREATE TABLE aml_cbr_advisory_ledger_entry (
    id                            UUID             NOT NULL,
    case_count                    INT              NOT NULL,
    avg_similarity                DOUBLE PRECISION NOT NULL,
    confidence                    DOUBLE PRECISION NOT NULL,
    predominant_outcome           VARCHAR(50),
    predominant_outcome_frequency DOUBLE PRECISION,
    recommended_capabilities      VARCHAR(1000),
    active                        BOOLEAN          NOT NULL DEFAULT false,
    CONSTRAINT pk_aml_cbr_advisory_ledger_entry PRIMARY KEY (id),
    CONSTRAINT fk_cbr_advisory_ledger_entry FOREIGN KEY (id) REFERENCES ledger_entry(id)
);

CREATE TABLE aml_supervisor_decision_ledger_entry (
    id                  UUID          NOT NULL,
    selected_bindings   VARCHAR(500)  NOT NULL,
    suppressed_bindings VARCHAR(500),
    rationale           VARCHAR(2000) NOT NULL,
    early_termination   BOOLEAN       NOT NULL,
    eligible_count      INT           NOT NULL,
    degraded            BOOLEAN       NOT NULL,
    CONSTRAINT pk_aml_supervisor_decision_ledger_entry PRIMARY KEY (id),
    CONSTRAINT fk_supervisor_decision_ledger_entry FOREIGN KEY (id) REFERENCES ledger_entry(id)
);
