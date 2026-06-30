CREATE TABLE aml_investigation_summary (
    id              UUID            NOT NULL,
    case_id         UUID            NOT NULL,
    status          VARCHAR(32)     NOT NULL,
    outcome_type    VARCHAR(64),
    transaction_id  VARCHAR(128)    NOT NULL,
    origin_account  VARCHAR(128)    NOT NULL,
    dest_account    VARCHAR(128)    NOT NULL,
    amount          DECIMAL(19,4)   NOT NULL,
    currency        VARCHAR(8)      NOT NULL,
    flag_reason     VARCHAR(128)    NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_aml_investigation_summary PRIMARY KEY (id),
    CONSTRAINT uq_aml_investigation_summary_case UNIQUE (case_id)
);

CREATE INDEX idx_aml_investigation_summary_status ON aml_investigation_summary (status);
CREATE INDEX idx_aml_investigation_summary_created ON aml_investigation_summary (created_at);
