-- Consolidated initial schema for aml-trust-routing.
-- Replaces V2004, V2009 incremental migrations (no production database exists).

CREATE TABLE aml_trust_routing_attestation (
    id                     UUID          NOT NULL,
    capability_tag         VARCHAR(100)  NOT NULL,
    selected_worker_id     VARCHAR(255)  NOT NULL,
    trust_score_at_routing DOUBLE PRECISION,
    threshold_applied      DOUBLE PRECISION NOT NULL,
    investigation_case_id  UUID          NOT NULL,
    reconstructed          BOOLEAN       NOT NULL DEFAULT FALSE,
    observer_failed        BOOLEAN       NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_aml_trust_routing_attestation PRIMARY KEY (id),
    CONSTRAINT fk_aml_trust_routing_ledger FOREIGN KEY (id) REFERENCES ledger_entry(id)
);
