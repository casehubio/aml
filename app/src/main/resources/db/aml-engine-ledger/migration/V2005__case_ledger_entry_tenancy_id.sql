-- V2005: add tenancy_id to case_ledger_entry
-- Column added to CaseLedgerEntry entity in casehub-engine (engine#403).
-- aml is the consumer that owns this join-table migration (V2000+).

ALTER TABLE case_ledger_entry ADD COLUMN IF NOT EXISTS tenancy_id VARCHAR(64) NOT NULL DEFAULT '';

CREATE INDEX IF NOT EXISTS idx_case_ledger_entry_tenancy_id ON case_ledger_entry (tenancy_id);
