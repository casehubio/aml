package io.casehub.aml.trust;

import io.casehub.aml.routing.AmlTrustRoutingPolicyProvider;
import io.casehub.api.spi.CaseOutcomeEvent;
import io.casehub.api.spi.CaseOutcomeObserver;
import io.casehub.engine.common.spi.event.WorkerDecisionEvent;
import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.api.spi.TrustScoreSource;
import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.platform.api.identity.TenancyConstants;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class AmlTrustRoutingObserver implements CaseOutcomeObserver {

    private static final Logger LOG = Logger.getLogger(AmlTrustRoutingObserver.class);

    private static final String ACTOR_ID   = "aml-orchestrator";
    private static final String ACTOR_ROLE = "AmlInvestigationOrchestrator";

    private final ConcurrentHashMap<UUID, Object> subjectLocks = new ConcurrentHashMap<>();

    @Inject
    TrustScoreSource              trustScoreSource;
    @Inject
    AmlTrustRoutingPolicyProvider policyProvider;
    @Inject
    AmlTrustAttestationRepository attestationRepo;
    @Inject
    AmlWorkerDecisionRepository   workerDecisionRepo;
    @PersistenceContext(unitName = "qhorus")
    EntityManager                 em;

    public void onWorkerDecision(@ObservesAsync WorkerDecisionEvent event) {
        final double threshold = policyProvider.forCapability(event.capabilityTag()).threshold();
        final Double score = trustScoreSource
                                     .capabilityScore(event.workerId(), event.capabilityTag())
                                     .stream().boxed().findFirst().orElse(null);
        final UUID attestationSubject = attestationSubjectFor(event.caseId());

        final Object lock = subjectLocks.computeIfAbsent(attestationSubject, k -> new Object());

        boolean attestationWritten = false;
        try {
            final AmlTrustRoutingAttestation entry = new AmlTrustRoutingAttestation();
            entry.id                  = UUID.randomUUID();
            entry.subjectId           = attestationSubject;
            entry.investigationCaseId = event.caseId();
            entry.capabilityTag       = event.capabilityTag();
            entry.selectedWorkerId    = event.workerId();
            entry.trustScoreAtRouting = score;
            entry.thresholdApplied    = threshold;
            entry.entryType           = LedgerEntryType.EVENT;
            entry.actorId             = ACTOR_ID;
            entry.actorType           = ActorType.SYSTEM;
            entry.actorRole           = ACTOR_ROLE;
            entry.occurredAt          = Instant.now();
            entry.reconstructed       = false;
            entry.observerFailed      = false;
            entry.tenancyId           = event.tenancyId() != null
                                        ? event.tenancyId() : TenancyConstants.DEFAULT_TENANT_ID;

            // Per-subject lock: REQUIRES_NEW must commit before releasing, preventing
            // concurrent observers from both reading max-sequence=null and assigning seq=1.
            synchronized (lock) {
                attestationRepo.saveWithSequence(entry);
            }
            attestationWritten = true;
        } catch (Exception e) {
            LOG.warnf(e, "AmlTrustRoutingObserver failed caseId=%s cap=%s workerId=%s",
                      event.caseId(), event.capabilityTag(), event.workerId());
            if (!attestationWritten) {
                try {
                    attestationRepo.saveObserverFailureEntry(event, attestationSubject, threshold);
                } catch (Exception inner) {
                    LOG.errorf(inner,
                               "AUDIT GAP: observer failure entry also failed caseId=%s cap=%s",
                               event.caseId(), event.capabilityTag());
                }
            }
        }
    }

    @Override
    public void onOutcome(CaseOutcomeEvent event) {
        if (!"aml-investigation".equals(event.caseType())) {return;}
        if (!"investigation-closed-no-sar".equals(event.outcomeLabel())) {return;}

        try {
            writeTriageOverruledAttestation(event);
        } catch (Exception e) {
            LOG.warnf(e, "Triage overruled attestation failed caseId=%s", event.caseId());
        }
    }

    // Namespaced subject UUID avoids IDX_LEDGER_ENTRY_SUBJECT_SEQ conflicts with investigation entries.
    static UUID attestationSubjectFor(UUID caseId) {
        return UUID.nameUUIDFromBytes(
                ("aml-trust-routing-attestation:" + caseId).getBytes(StandardCharsets.UTF_8));
    }

    private void writeTriageOverruledAttestation(CaseOutcomeEvent event) {
        var triageEntry = workerDecisionRepo.findLatestByCaseIdAndCapability(
                event.caseId(), "investigation-triage");
        if (triageEntry.isEmpty()) {
            LOG.warnf("No WorkerDecisionEntry for investigation-triage caseId=%s — skipping overruled attestation",
                      event.caseId());
            return;
        }

        String overruleSource = determineOverruleSource(event.caseFileSnapshot());

        QuarkusTransaction.requiringNew().run(() -> {
            var attestation = new LedgerAttestation();
            attestation.id             = UUID.randomUUID();
            attestation.ledgerEntryId  = triageEntry.get().id;
            attestation.subjectId      = event.caseId();
            attestation.attestorId     = ACTOR_ID;
            attestation.attestorType   = ActorType.SYSTEM;
            attestation.attestorRole   = "TriageOutcomeFeedback";
            attestation.verdict        = AttestationVerdict.CHALLENGED;
            attestation.capabilityTag  = "investigation-triage";
            attestation.trustDimension = "investigation-accuracy";
            attestation.dimensionScore = 0.2;
            attestation.confidence     = 1.0;
            attestation.occurredAt     = event.closedAt() != null ? event.closedAt() : Instant.now();
            attestation.evidence       = "TRIAGE_OVERRULED: originalDecision=SAR_WARRANTED, overruleSource=" + overruleSource;
            em.persist(attestation);
        });
    }

    private String determineOverruleSource(Map<String, Object> snapshot) {
        if (snapshot.get("rejectionEscalation") instanceof Map<?, ?> esc
            && "NO_SAR".equals(esc.get("decision"))) {
            return "ESCALATION_NO_SAR";
        }
        if (snapshot.get("postRejectionTriage") instanceof Map<?, ?> triage
            && "FALSE_POSITIVE".equals(triage.get("decision"))) {
            return "RE_TRIAGE_DROP";
        }
        return "UNKNOWN";
    }
}
