package io.casehub.aml.trust;

import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.model.WorkerDecisionEntry;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.platform.api.identity.TenancyConstants;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import org.hibernate.exception.ConstraintViolationException;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Layer 9: lazily fills attestation gaps when compliance evidence is read.
 *
 * <p>Called from {@link io.casehub.aml.compliance.AmlComplianceEvidenceService#buildTrustRouting}
 * on every GET of the compliance evidence endpoint. For each capability dispatched by the engine
 * (via WorkerDecisionEntry) that has no AmlTrustRoutingAttestation, writes a reconstructed entry
 * using the authoritative trust data from WorkerDecisionEntry.
 *
 * <p>Idempotent: capabilities already covered (any reconstructed/observerFailed value) are skipped.
 * Multi-JVM safe via per-JVM ConcurrentHashMap lock; on production PostgreSQL a partial unique
 * index (UQ_TRUST_ATTEST_CASE_CAP_RECONSTRUCTED) provides DB-level idempotency (see aml#57).
 */
@ApplicationScoped
public class AmlAttestationReconciler {

    private static final Logger LOG = Logger.getLogger(AmlAttestationReconciler.class);
    private static final String ACTOR_ID = "aml-orchestrator";
    private static final String ACTOR_ROLE = "AmlInvestigationOrchestrator";

    private final ConcurrentHashMap<UUID, Object> subjectLocks = new ConcurrentHashMap<>();
    private final AmlTrustAttestationRepository attestationRepo;

    @Inject
    public AmlAttestationReconciler(AmlTrustAttestationRepository attestationRepo) {
        this.attestationRepo = attestationRepo;
    }

    /**
     * Checks for attestation gaps and writes reconstructed entries for any missing capabilities.
     *
     * @param caseId     the investigation case UUID
     * @param dispatched all WorkerDecisionEntry records for this case (from the engine)
     * @param existing   all existing AmlTrustRoutingAttestation records for this case
     * @return existing list + any newly written reconstructed entries
     */
    public List<AmlTrustRoutingAttestation> reconcileIfNeeded(
            final UUID caseId,
            final List<WorkerDecisionEntry> dispatched,
            final List<AmlTrustRoutingAttestation> existing) {

        final Set<String> coveredCaps = new HashSet<>();
        for (AmlTrustRoutingAttestation a : existing) {
            if (a.capabilityTag != null) coveredCaps.add(a.capabilityTag);
        }

        final List<AmlTrustRoutingAttestation> result = new ArrayList<>(existing);
        final UUID attestationSubject = AmlTrustRoutingObserver.attestationSubjectFor(caseId);
        final Object lock = subjectLocks.computeIfAbsent(attestationSubject, k -> new Object());

        for (WorkerDecisionEntry decision : dispatched) {
            final String capTag = decision.capabilityTag;
            if (capTag == null || coveredCaps.contains(capTag)) continue;

            final AmlTrustRoutingAttestation entry = new AmlTrustRoutingAttestation();
            entry.id = UUID.randomUUID();
            entry.subjectId = attestationSubject;
            entry.investigationCaseId = caseId;
            entry.capabilityTag = capTag;
            entry.selectedWorkerId = decision.workerId;
            entry.trustScoreAtRouting = decision.trustScoreAtRouting;
            entry.thresholdApplied = decision.thresholdApplied != null
                    ? decision.thresholdApplied : 0.0;
            entry.entryType = LedgerEntryType.EVENT;
            entry.actorId = ACTOR_ID;
            entry.actorType = ActorType.SYSTEM;
            entry.actorRole = ACTOR_ROLE;
            entry.occurredAt = Instant.now();
            entry.reconstructed = true;
            entry.observerFailed = false;
            entry.tenancyId = TenancyConstants.DEFAULT_TENANT_ID;

            try {
                synchronized (lock) {
                    attestationRepo.saveWithSequence(entry);
                }
                result.add(entry);
                coveredCaps.add(capTag);
            } catch (PersistenceException e) {
                if (e.getCause() instanceof ConstraintViolationException) {
                    // Forward-compatible guard for UQ_TRUST_ATTEST_CASE_CAP_RECONSTRUCTED (aml#57).
                    // This index is applied on production PostgreSQL but not H2 (H2 doesn't support
                    // partial unique indexes), so this catch is inactive in tests. Once aml#57 lands,
                    // this prevents multi-JVM duplicate reconstructed entries.
                    // Peer's entry is in DB but absent from this request's merged list;
                    // status correctly shows PARTIAL for this request; next request reads correctly.
                    LOG.debugf("Peer JVM reconciled caseId=%s cap=%s — skipping duplicate",
                            caseId, capTag);
                } else {
                    throw e;
                }
            }
        }

        return result;
    }
}
