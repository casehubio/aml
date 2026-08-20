package io.casehub.aml.trust;

import io.casehub.api.spi.CaseOutcomeEvent;
import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.platform.api.identity.TenancyConstants;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class TriageOverruledAttestationTest {

    @Inject AmlTrustRoutingObserver observer;

    @PersistenceContext(unitName = "qhorus")
    EntityManager em;

    @Test
    void escalation_no_sar_writes_challenged_attestation() {
        final UUID caseId = UUID.randomUUID();
        final UUID entryId = insertTriageWorkerDecisionEntry(caseId);

        final CaseOutcomeEvent event = new CaseOutcomeEvent(
                "aml-investigation",
                TenancyConstants.DEFAULT_TENANT_ID,
                caseId,
                Map.of("rejectionEscalation", Map.of("decision", "NO_SAR"),
                       "investigationTriage", Map.of("decision", "SAR_WARRANTED")),
                "investigation-closed-no-sar",
                Instant.now(),
                Map.of());

        observer.onOutcome(event);

        final List<LedgerAttestation> attestations = QuarkusTransaction.requiringNew().call(() ->
                em.createQuery(
                        "SELECT a FROM LedgerAttestation a WHERE a.subjectId = :sid AND a.trustDimension = :dim",
                        LedgerAttestation.class)
                    .setParameter("sid", caseId)
                    .setParameter("dim", "investigation-accuracy")
                    .getResultList());

        assertEquals(1, attestations.size());
        final LedgerAttestation a = attestations.get(0);
        assertEquals(AttestationVerdict.CHALLENGED, a.verdict);
        assertEquals("investigation-triage", a.capabilityTag);
        assertEquals("investigation-accuracy", a.trustDimension);
        assertEquals(0.2, a.dimensionScore, 0.001);
        assertEquals(1.0, a.confidence, 0.001);
        assertEquals("aml-orchestrator", a.attestorId);
        assertEquals(ActorType.SYSTEM, a.attestorType);
        assertEquals("TriageOutcomeFeedback", a.attestorRole);
        assertEquals(entryId, a.ledgerEntryId);
        assertTrue(a.evidence.contains("TRIAGE_OVERRULED"));
        assertTrue(a.evidence.contains("ESCALATION_NO_SAR"));
    }

    @Test
    void re_triage_drop_writes_attestation_with_re_triage_evidence() {
        final UUID caseId = UUID.randomUUID();
        insertTriageWorkerDecisionEntry(caseId);

        final CaseOutcomeEvent event = new CaseOutcomeEvent(
                "aml-investigation",
                TenancyConstants.DEFAULT_TENANT_ID,
                caseId,
                Map.of("postRejectionTriage", Map.of("decision", "FALSE_POSITIVE"),
                       "actionGateRejected", Map.of("actionType", "sar.filing"),
                       "investigationTriage", Map.of("decision", "SAR_WARRANTED")),
                "investigation-closed-no-sar",
                Instant.now(),
                Map.of());

        observer.onOutcome(event);

        final List<LedgerAttestation> attestations = QuarkusTransaction.requiringNew().call(() ->
                                                                                                    em.createQuery(
                                                                                                              "SELECT a FROM LedgerAttestation a WHERE a.subjectId = :sid AND a.trustDimension = :dim",
                                                                                                              LedgerAttestation.class)
                                                                                                      .setParameter("sid", caseId)
                                                                                                      .setParameter("dim", "investigation-accuracy")
                                                                                                      .getResultList());

        assertEquals(1, attestations.size());
        assertEquals(AttestationVerdict.CHALLENGED, attestations.get(0).verdict);
        assertTrue(attestations.get(0).evidence.contains("RE_TRIAGE_DROP"));
    }

    @Test
    void investigation_complete_outcome_does_not_write_attestation() {
        final UUID caseId = UUID.randomUUID();
        insertTriageWorkerDecisionEntry(caseId);

        final CaseOutcomeEvent event = new CaseOutcomeEvent(
                "aml-investigation",
                TenancyConstants.DEFAULT_TENANT_ID,
                caseId,
                Map.of("investigationTriage", Map.of("decision", "SAR_WARRANTED")),
                "investigation-complete",
                Instant.now(),
                Map.of());

        observer.onOutcome(event);

        final List<LedgerAttestation> attestations = QuarkusTransaction.requiringNew().call(() ->
                                                                                                    em.createQuery(
                                                                                                              "SELECT a FROM LedgerAttestation a WHERE a.subjectId = :sid AND a.trustDimension = :dim",
                                                                                                              LedgerAttestation.class)
                                                                                                      .setParameter("sid", caseId)
                                                                                                      .setParameter("dim", "investigation-accuracy")
                                                                                                      .getResultList());

        assertEquals(0, attestations.size());
    }

    @Test
    void investigation_cleared_outcome_does_not_write_attestation() {
        final UUID caseId = UUID.randomUUID();
        insertTriageWorkerDecisionEntry(caseId);

        final CaseOutcomeEvent event = new CaseOutcomeEvent(
                "aml-investigation",
                TenancyConstants.DEFAULT_TENANT_ID,
                caseId,
                Map.of("investigationTriage", Map.of("decision", "FALSE_POSITIVE")),
                "investigation-cleared",
                Instant.now(),
                Map.of());

        observer.onOutcome(event);

        final List<LedgerAttestation> attestations = QuarkusTransaction.requiringNew().call(() ->
                                                                                                    em.createQuery(
                                                                                                              "SELECT a FROM LedgerAttestation a WHERE a.subjectId = :sid AND a.trustDimension = :dim",
                                                                                                              LedgerAttestation.class)
                                                                                                      .setParameter("sid", caseId)
                                                                                                      .setParameter("dim", "investigation-accuracy")
                                                                                                      .getResultList());

        assertEquals(0, attestations.size());
    }

    @Test
    void non_aml_case_type_does_not_write_attestation() {
        final UUID caseId = UUID.randomUUID();
        insertTriageWorkerDecisionEntry(caseId);

        final CaseOutcomeEvent event = new CaseOutcomeEvent(
                "other-case-type",
                TenancyConstants.DEFAULT_TENANT_ID,
                caseId,
                Map.of(),
                "investigation-closed-no-sar",
                Instant.now(),
                Map.of());

        observer.onOutcome(event);

        final List<LedgerAttestation> attestations = QuarkusTransaction.requiringNew().call(() ->
                                                                                                    em.createQuery(
                                                                                                              "SELECT a FROM LedgerAttestation a WHERE a.subjectId = :sid AND a.trustDimension = :dim",
                                                                                                              LedgerAttestation.class)
                                                                                                      .setParameter("sid", caseId)
                                                                                                      .setParameter("dim", "investigation-accuracy")
                                                                                                      .getResultList());

        assertEquals(0, attestations.size());
    }

    @Test
    void missing_triage_entry_does_not_throw() {
        final UUID caseId = UUID.randomUUID();

        final CaseOutcomeEvent event = new CaseOutcomeEvent(
                "aml-investigation",
                TenancyConstants.DEFAULT_TENANT_ID,
                caseId,
                Map.of("rejectionEscalation", Map.of("decision", "NO_SAR")),
                "investigation-closed-no-sar",
                Instant.now(),
                Map.of());

        assertDoesNotThrow(() -> observer.onOutcome(event));
    }


    private UUID insertTriageWorkerDecisionEntry(UUID caseId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            final UUID entryId = UUID.randomUUID();
            em.createNativeQuery(
                    "INSERT INTO ledger_entry (id, dtype, subject_id, sequence_number, entry_type, actor_id, actor_type, occurred_at, tenancy_id)" +
                    " VALUES (:id, 'WORKER_DECISION', :sid, 1, 'EVENT', :wid, 'SYSTEM', CURRENT_TIMESTAMP, :tid)")
                .setParameter("id", entryId)
                .setParameter("sid", caseId)
                .setParameter("wid", "investigation-triage-agent")
                .setParameter("tid", TenancyConstants.DEFAULT_TENANT_ID)
                .executeUpdate();
            em.createNativeQuery(
                    "INSERT INTO worker_decision_entry (id, worker_id, capability_tag, case_id)" +
                    " VALUES (:id, :wid, :cap, :cid)")
                .setParameter("id", entryId)
                .setParameter("wid", "investigation-triage-agent")
                .setParameter("cap", "investigation-triage")
                .setParameter("cid", caseId)
                .executeUpdate();
            return entryId;
        });
    }
}
