CREATE TABLE aml_supervisor_decision_ledger_entry (
    id UUID NOT NULL,
    selected_bindings VARCHAR(500) NOT NULL,
    suppressed_bindings VARCHAR(500),
    rationale VARCHAR(2000) NOT NULL,
    early_termination BOOLEAN NOT NULL,
    eligible_count INT NOT NULL,
    degraded BOOLEAN NOT NULL,
    CONSTRAINT fk_supervisor_decision_ledger_entry FOREIGN KEY (id) REFERENCES ledger_entry(id)
);
