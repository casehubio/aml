-- V2006: add tenancy_id to worker_decision_entry
-- Column added to WorkerDecisionEntry entity in casehub-engine (engine#403).
-- aml is the consumer that owns this join-table migration (V2000+).

ALTER TABLE worker_decision_entry ADD COLUMN IF NOT EXISTS tenancy_id VARCHAR(64) NOT NULL DEFAULT '';

CREATE INDEX IF NOT EXISTS idx_worker_decision_entry_tenancy_id ON worker_decision_entry (tenancy_id);
