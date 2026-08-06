package io.casehub.aml.trust;

import io.casehub.aml.domain.SarOutcome;
import io.casehub.aml.domain.SarVerdict;
import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class SarOutcomeFeedbackServiceTest {

    @Inject
    SarOutcomeFeedbackService feedbackService;

    @PersistenceContext(unitName = "qhorus")
    EntityManager em;

    @Test
    void no_worker_decision_entry_does_not_throw() {
        assertDoesNotThrow(() ->
                feedbackService.recordOutcome(UUID.randomUUID(),
                        new SarOutcome(SarVerdict.UPHELD, "SAR upheld", 0.9)));
    }

    @Test
    @Transactional
    void upheld_verdict_writes_sound_attestation() {
        final UUID caseId = UUID.randomUUID();
        insertWorkerDecisionEntry(caseId, "sar-drafting-agent-senior", "sar-drafting");

        feedbackService.recordOutcome(caseId, new SarOutcome(SarVerdict.UPHELD, "SAR upheld by FinCEN", 0.92));

        final List<LedgerAttestation> attestations = em.createQuery(
                "SELECT a FROM LedgerAttestation a WHERE a.subjectId = :sid", LedgerAttestation.class)
                .setParameter("sid", caseId)
                .getResultList();
        assertEquals(1, attestations.size());
        final LedgerAttestation a = attestations.get(0);
        assertEquals(AttestationVerdict.SOUND, a.verdict);
        assertEquals("sar-drafting", a.capabilityTag);
        assertEquals("investigation-accuracy", a.trustDimension);
        assertEquals(0.92, a.dimensionScore, 0.001);
        assertEquals(1.0, a.confidence, 0.001);
        assertEquals("aml-compliance-system", a.attestorId);
    }

    @Test
    @Transactional
    void flagged_verdict_writes_flagged_attestation() {
        final UUID caseId = UUID.randomUUID();
        insertWorkerDecisionEntry(caseId, "sar-drafting-agent-senior", "sar-drafting");

        feedbackService.recordOutcome(caseId, new SarOutcome(SarVerdict.FLAGGED, "Incomplete evidence", 0.25));

        final List<LedgerAttestation> attestations = em.createQuery(
                "SELECT a FROM LedgerAttestation a WHERE a.subjectId = :sid", LedgerAttestation.class)
                .setParameter("sid", caseId)
                .getResultList();
        assertEquals(1, attestations.size());
        assertEquals(AttestationVerdict.FLAGGED, attestations.get(0).verdict);
        assertEquals(0.25, attestations.get(0).dimensionScore, 0.001);
    }

    @Test
    @Transactional
    void withdrawn_verdict_writes_flagged_attestation() {
        final UUID caseId = UUID.randomUUID();
        insertWorkerDecisionEntry(caseId, "sar-drafting-agent-senior", "sar-drafting");

        feedbackService.recordOutcome(caseId, new SarOutcome(SarVerdict.WITHDRAWN, "SAR withdrawn", 0.10));

        final List<LedgerAttestation> attestations = em.createQuery(
                "SELECT a FROM LedgerAttestation a WHERE a.subjectId = :sid", LedgerAttestation.class)
                .setParameter("sid", caseId)
                .getResultList();
        assertEquals(AttestationVerdict.FLAGGED, attestations.get(0).verdict);
    }

    /**
     * Inserts a minimal ledger_entry + worker_decision_entry row pair for testing.
     *
     * <p>ledger_entry requires: id, dtype (discriminator), subject_id, sequence_number,
     * entry_type, occurred_at. All other columns are nullable after V1002 dropped the
     * optional supplement fields.
     *
     * <p>worker_decision_entry requires: id (FK to ledger_entry), worker_id, case_id.
     */
    private void insertWorkerDecisionEntry(final UUID caseId, final String workerId, final String capabilityTag) {
        final UUID entryId = UUID.randomUUID();
        em.createNativeQuery(
                "INSERT INTO ledger_entry (id, dtype, subject_id, sequence_number, entry_type, actor_id, actor_type, occurred_at)" +
                " VALUES (:id, 'WORKER_DECISION', :sid, 1, 'EVENT', :wid, 'SYSTEM', CURRENT_TIMESTAMP)")
                .setParameter("id", entryId)
                .setParameter("sid", caseId)
                .setParameter("wid", workerId)
                .executeUpdate();
        em.createNativeQuery(
                "INSERT INTO worker_decision_entry (id, worker_id, capability_tag, case_id)" +
                " VALUES (:id, :wid, :cap, :cid)")
                .setParameter("id", entryId)
                .setParameter("wid", workerId)
                .setParameter("cap", capabilityTag)
                .setParameter("cid", caseId)
                .executeUpdate();
    }
}
