package io.casehub.aml.trust;

import io.casehub.engine.common.spi.event.WorkerDecisionEvent;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.platform.api.identity.TenancyConstants;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class AmlTrustAttestationRepository {

    @PersistenceContext
    EntityManager em;

    @Transactional(TxType.REQUIRED)
    public AmlTrustRoutingAttestation save(AmlTrustRoutingAttestation entry) {
        em.persist(entry);
        return entry;
    }

    @Transactional(TxType.REQUIRED)
    public List<AmlTrustRoutingAttestation> findByInvestigationCaseId(UUID caseId) {
        return em.createQuery(
                "SELECT a FROM AmlTrustRoutingAttestation a" +
                " WHERE a.investigationCaseId = :caseId ORDER BY a.occurredAt ASC",
                AmlTrustRoutingAttestation.class)
                .setParameter("caseId", caseId)
                .getResultList();
    }

    /**
     * Assigns the next sequence number and persists the attestation in a single
     * {@code REQUIRES_NEW} transaction. Must only be called from within a
     * serialization scope (e.g., a {@code synchronized} block held by the caller)
     * so that the transaction commits before the lock is released, preventing
     * concurrent calls from both reading {@code max=null} and both assigning seq=1.
     *
     * <p>Queries across all {@code LedgerEntry} subtypes — the
     * {@code IDX_LEDGER_ENTRY_SUBJECT_SEQ} constraint is on the base table.
     */
    @Transactional(TxType.REQUIRES_NEW)
    public AmlTrustRoutingAttestation saveWithSequence(AmlTrustRoutingAttestation entry) {
        Integer max = em.createQuery(
                "SELECT MAX(e.sequenceNumber) FROM LedgerEntry e" +
                " WHERE e.subjectId = :sid",
                Integer.class)
                .setParameter("sid", entry.subjectId)
                .getSingleResult();
        entry.sequenceNumber = max == null ? 1 : max + 1;
        em.persist(entry);
        return entry;
    }

    /**
     * Writes an observer-failure attestation entry in an isolated REQUIRES_NEW transaction.
     *
     * <p>Called from {@link AmlTrustRoutingObserver} outer catch when the main attestation write fails.
     * Per PP-20260530-49856c: failure entry writer uses REQUIRES_NEW so the record commits
     * independently regardless of any surrounding transaction state.
     *
     * @param event     the WorkerDecisionEvent that triggered the observer
     * @param subject   the attestation-specific subject UUID (derived by the observer)
     * @param threshold the trust threshold from the policy provider (computed before the try block)
     */
    @Transactional(TxType.REQUIRES_NEW)
    public AmlTrustRoutingAttestation saveObserverFailureEntry(
            final WorkerDecisionEvent event,
            final UUID subject,
            final double threshold) {
        Integer max = em.createQuery(
                        "SELECT MAX(e.sequenceNumber) FROM LedgerEntry e WHERE e.subjectId = :sid",
                        Integer.class)
                .setParameter("sid", subject)
                .getSingleResult();
        final AmlTrustRoutingAttestation entry = new AmlTrustRoutingAttestation();
        entry.id = UUID.randomUUID();
        entry.subjectId = subject;
        entry.investigationCaseId = event.caseId();
        entry.capabilityTag = event.capabilityTag();
        entry.selectedWorkerId = event.workerId();
        entry.trustScoreAtRouting = null;
        entry.thresholdApplied = threshold;
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = "aml-orchestrator";
        entry.actorType = ActorType.SYSTEM;
        entry.actorRole = "AmlInvestigationOrchestrator-observer-failed";
        entry.occurredAt = Instant.now();
        entry.sequenceNumber = max == null ? 1 : max + 1;
        entry.reconstructed = false;
        entry.observerFailed = true;
        entry.tenancyId = event.tenancyId() != null
                ? event.tenancyId() : TenancyConstants.DEFAULT_TENANT_ID;
        em.persist(entry);
        return entry;
    }
}
