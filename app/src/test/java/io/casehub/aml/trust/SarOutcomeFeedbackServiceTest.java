package io.casehub.aml.trust;

import io.casehub.aml.domain.SarOutcome;
import io.casehub.aml.domain.SarVerdict;
import io.casehub.engine.common.spi.cache.CaseInstanceCache;
import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class SarOutcomeFeedbackServiceTest {

    @Inject
    SarOutcomeFeedbackService feedbackService;

    @PersistenceContext(unitName = "qhorus")
    EntityManager em;
    @Inject
    CaseInstanceCache caseInstanceCache;


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

    @Test
    @Transactional
    void pep_case_writes_pep_clearance_attestation_on_osint_worker() {
        final UUID caseId = UUID.randomUUID();
        insertWorkerDecisionEntry(caseId, "sar-drafting-agent-senior", "sar-drafting", 1);
        insertWorkerDecisionEntry(caseId, "osint-screening-agent-senior", "osint-screening", 2);
        seedCaseContext(caseId, Map.of(
                "entityResolution", Map.of("entityType", "PEP", "riskScore", 0.87),
                "osintScreening", Map.of("declined", false)));

        feedbackService.recordOutcome(caseId, new SarOutcome(SarVerdict.UPHELD, "SAR upheld", 0.92));

        final List<LedgerAttestation> attestations = em.createQuery(
                                                               "SELECT a FROM LedgerAttestation a WHERE a.subjectId = :sid ORDER BY a.trustDimension",
                                                               LedgerAttestation.class)
                                                       .setParameter("sid", caseId)
                                                       .getResultList();
        assertEquals(2, attestations.size());
        assertEquals("investigation-accuracy", attestations.get(0).trustDimension);
        assertEquals("sar-drafting", attestations.get(0).capabilityTag);
        assertEquals("pep-clearance", attestations.get(1).trustDimension);
        assertEquals("osint-screening", attestations.get(1).capabilityTag);
        assertEquals(0.92, attestations.get(1).dimensionScore, 0.001);
    }

    @Test
    @Transactional
    void non_pep_case_does_not_write_pep_clearance_attestation() {
        final UUID caseId = UUID.randomUUID();
        insertWorkerDecisionEntry(caseId, "sar-drafting-agent-senior", "sar-drafting", 1);
        insertWorkerDecisionEntry(caseId, "osint-screening-agent-senior", "osint-screening", 2);
        seedCaseContext(caseId, Map.of(
                "entityResolution", Map.of("entityType", "CORPORATE", "riskScore", 0.35)));

        feedbackService.recordOutcome(caseId, new SarOutcome(SarVerdict.UPHELD, "SAR upheld", 0.90));

        final List<LedgerAttestation> attestations = em.createQuery(
                                                               "SELECT a FROM LedgerAttestation a WHERE a.subjectId = :sid", LedgerAttestation.class)
                                                       .setParameter("sid", caseId)
                                                       .getResultList();
        assertEquals(1, attestations.size());
        assertEquals("investigation-accuracy", attestations.get(0).trustDimension);
    }

    @Test
    @Transactional
    void declined_osint_writes_scope_awareness_attestation() {
        final UUID caseId = UUID.randomUUID();
        insertWorkerDecisionEntry(caseId, "sar-drafting-agent-senior", "sar-drafting", 1);
        insertWorkerDecisionEntry(caseId, "osint-screening-agent-senior", "osint-screening", 2);
        seedCaseContext(caseId, Map.of(
                "entityResolution", Map.of("entityType", "CORPORATE"),
                "osintScreening", Map.of("declined", true, "reason", "insufficient clearance")));

        feedbackService.recordOutcome(caseId, new SarOutcome(SarVerdict.UPHELD, "SAR upheld", 0.88));

        final List<LedgerAttestation> attestations = em.createQuery(
                                                               "SELECT a FROM LedgerAttestation a WHERE a.subjectId = :sid ORDER BY a.trustDimension",
                                                               LedgerAttestation.class)
                                                       .setParameter("sid", caseId)
                                                       .getResultList();
        assertEquals(2, attestations.size());
        assertEquals("investigation-accuracy", attestations.get(0).trustDimension);
        assertEquals("scope-awareness", attestations.get(1).trustDimension);
        assertEquals("osint-screening", attestations.get(1).capabilityTag);
        assertEquals(1.0, attestations.get(1).dimensionScore, 0.001);
    }


    private void insertWorkerDecisionEntry(final UUID caseId, final String workerId, final String capabilityTag) {
        insertWorkerDecisionEntry(caseId, workerId, capabilityTag, 1);
    }

    private void insertWorkerDecisionEntry(final UUID caseId, final String workerId,
                                           final String capabilityTag, final int sequenceNumber) {
        final UUID entryId = UUID.randomUUID();
        em.createNativeQuery(
                  "INSERT INTO ledger_entry (id, dtype, subject_id, sequence_number, entry_type, actor_id, actor_type, occurred_at)" +
                  " VALUES (:id, 'WORKER_DECISION', :sid, :seq, 'EVENT', :wid, 'SYSTEM', CURRENT_TIMESTAMP)")
          .setParameter("id", entryId)
          .setParameter("sid", caseId)
          .setParameter("seq", sequenceNumber)
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

    private void seedCaseContext(final UUID caseId, final Map<String, Object> context) {
        final var instance = new io.casehub.engine.common.internal.model.CaseInstance();
        instance.setUuid(caseId);
        instance.setCaseContext(new io.casehub.engine.internal.context.CaseContextImpl(context));
        instance.setState(io.casehub.api.model.CaseStatus.COMPLETED);
        caseInstanceCache.put(instance);
    }

}
