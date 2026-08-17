-- Consolidated initial schema for aml-ledger.
-- Replaces V2007, V2010, V2012, V2013 incremental migrations (no production database exists).

CREATE TABLE aml_case_opened_ledger_entry (
    id                     UUID         NOT NULL,
    transaction_id         VARCHAR(255) NOT NULL,
    origin_account_id      VARCHAR(255) NOT NULL,
    destination_account_id VARCHAR(255) NOT NULL,
    CONSTRAINT pk_aml_case_opened PRIMARY KEY (id),
    CONSTRAINT fk_aml_case_opened FOREIGN KEY (id) REFERENCES ledger_entry (id)
);

CREATE TABLE aml_compliance_review_ledger_entry (
    id      UUID         NOT NULL,
    task_id VARCHAR(255) NOT NULL,
    CONSTRAINT pk_aml_compliance_review PRIMARY KEY (id),
    CONSTRAINT fk_aml_compliance_review FOREIGN KEY (id) REFERENCES ledger_entry (id)
);

CREATE TABLE aml_sar_officer_reviewed_ledger_entry (
    id               UUID         NOT NULL,
    review_decision  VARCHAR(20)  NOT NULL,
    rejection_reason VARCHAR(1000),
    CONSTRAINT pk_aml_sar_officer_reviewed PRIMARY KEY (id),
    CONSTRAINT fk_aml_sar_officer_reviewed_ledger FOREIGN KEY (id) REFERENCES ledger_entry(id)
);

CREATE TABLE aml_entity_erasure_entry (
    id               UUID         NOT NULL,
    erased_entity_id VARCHAR(255) NOT NULL,
    erasure_reason   VARCHAR(50)  NOT NULL,
    memories_erased  INT          NOT NULL,
    CONSTRAINT pk_aml_entity_erasure_entry PRIMARY KEY (id),
    CONSTRAINT fk_aml_entity_erasure_entry_ledger
        FOREIGN KEY (id) REFERENCES ledger_entry(id)
);
