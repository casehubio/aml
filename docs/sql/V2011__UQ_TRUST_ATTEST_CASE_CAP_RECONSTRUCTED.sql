-- PostgreSQL only — H2 does not support partial unique indexes even in MODE=PostgreSQL.
-- Apply manually on the production PostgreSQL database after V2004 has run.
-- Do NOT place in db/migration/ paths — Flyway will fail on H2.
--
-- The AmlAttestationReconciler catches PersistenceException(ConstraintViolationException)
-- to handle idempotent duplicate writes — this catch is dead code in H2 tests but
-- active on production PostgreSQL once this index is applied.
CREATE UNIQUE INDEX IF NOT EXISTS UQ_TRUST_ATTEST_CASE_CAP_RECONSTRUCTED
    ON aml_trust_routing_attestation (investigation_case_id, capability_tag)
    WHERE reconstructed = TRUE;
