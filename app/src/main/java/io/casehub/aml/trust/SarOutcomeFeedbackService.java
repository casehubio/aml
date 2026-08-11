package io.casehub.aml.trust;

import io.casehub.aml.domain.SarOutcome;
import io.casehub.aml.domain.SarVerdict;
import io.casehub.aml.engine.SarOutcomeRecordedEvent;
import io.casehub.engine.common.spi.cache.CaseInstanceCache;
import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.model.WorkerDecisionEntry;
import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.platform.api.identity.ActorType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Records a SAR outcome as a {@link LedgerAttestation} on the worker decision entry
 * that performed the {@code sar-drafting} capability for the given case.
 *
 * <p>Called after a SAR has been reviewed externally (e.g. upheld by FinCEN, withdrawn,
 * or flagged as deficient). The attestation updates the trust record for the drafting
 * agent so that future trust-weighted routing prefers agents with better SAR outcomes.
 *
 * <p>Layer 6 tutorial component — maps SAR outcome feedback into the
 * {@code investigation-accuracy} trust dimension.
 */
@ApplicationScoped
public class SarOutcomeFeedbackService {

    private static final Logger LOG = Logger.getLogger(SarOutcomeFeedbackService.class);

    @PersistenceContext(unitName = "qhorus")
    EntityManager em;

    @Inject
    AmlWorkerDecisionRepository workerDecisionRepo;

    @Inject
    CaseInstanceCache caseInstanceCache;

    @Transactional
    public void recordOutcome(final UUID caseId, final SarOutcome outcome) {
        final Optional<WorkerDecisionEntry> sarEntry =
                workerDecisionRepo.findLatestByCaseIdAndCapability(caseId, "sar-drafting");

        if (sarEntry.isEmpty()) {
            LOG.warnf("No WorkerDecisionEntry found for caseId=%s capability=sar-drafting — skipping attestation", caseId);
            return;
        }

        writeAttestation(sarEntry.get(), caseId, "sar-drafting",
                         "investigation-accuracy", outcome.investigationAccuracyScore(), outcome);

        writePepClearanceIfApplicable(caseId, outcome);
        writeScopeAwarenessIfApplicable(caseId, outcome);
    }

    public void onSarOutcome(@Observes SarOutcomeRecordedEvent event) {
        recordOutcome(event.caseId(), event.outcome());
    }

    private void writeAttestation(final WorkerDecisionEntry entry, final UUID caseId,
                                  final String capabilityTag, final String trustDimension,
                                  final double dimensionScore, final SarOutcome outcome) {
        final LedgerAttestation attestation = new LedgerAttestation();
        attestation.id             = UUID.randomUUID();
        attestation.ledgerEntryId  = entry.id;
        attestation.subjectId      = caseId;
        attestation.attestorId     = "aml-compliance-system";
        attestation.attestorType   = ActorType.SYSTEM;
        attestation.attestorRole   = "SarOutcomeFeedback";
        attestation.verdict        = toVerdict(outcome.verdict());
        attestation.capabilityTag  = capabilityTag;
        attestation.trustDimension = trustDimension;
        attestation.dimensionScore = dimensionScore;
        attestation.confidence     = 1.0;
        attestation.occurredAt     = Instant.now();
        attestation.evidence       = outcome.reason();
        em.persist(attestation);
    }

    private void writePepClearanceIfApplicable(final UUID caseId, final SarOutcome outcome) {
        final Optional<WorkerDecisionEntry> osintEntry =
                workerDecisionRepo.findLatestByCaseIdAndCapability(caseId, "osint-screening");
        if (osintEntry.isEmpty()) {
            return;
        }
        final var instance = caseInstanceCache.get(caseId);
        if (instance == null) {
            return;
        }
        final Object entityRes = instance.getCaseContext().get("entityResolution");
        if (!(entityRes instanceof Map<?, ?> entityMap)) {
            return;
        }
        if (!"PEP".equals(entityMap.get("entityType"))) {
            return;
        }
        writeAttestation(osintEntry.get(), caseId, "osint-screening",
                         "pep-clearance", outcome.investigationAccuracyScore(), outcome);
    }

    private void writeScopeAwarenessIfApplicable(final UUID caseId, final SarOutcome outcome) {
        final var instance = caseInstanceCache.get(caseId);
        if (instance == null) {
            return;
        }
        final Object osint = instance.getCaseContext().get("osintScreening");
        if (!(osint instanceof Map<?, ?> osintMap)) {
            return;
        }
        if (!Boolean.TRUE.equals(osintMap.get("declined"))) {
            return;
        }
        final Optional<WorkerDecisionEntry> osintEntry =
                workerDecisionRepo.findLatestByCaseIdAndCapability(caseId, "osint-screening");
        if (osintEntry.isEmpty()) {
            return;
        }
        writeAttestation(osintEntry.get(), caseId, "osint-screening",
                         "scope-awareness", 1.0, outcome);
    }

    private AttestationVerdict toVerdict(final SarVerdict verdict) {
        return switch (verdict) {
            case UPHELD -> AttestationVerdict.SOUND;
            case WITHDRAWN, FLAGGED -> AttestationVerdict.FLAGGED;
        };
    }
}
