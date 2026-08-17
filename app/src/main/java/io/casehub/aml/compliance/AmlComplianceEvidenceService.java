package io.casehub.aml.compliance;

import io.casehub.aml.ledger.AmlCaseOpenedLedgerEntry;
import io.casehub.aml.ledger.AmlCaseProfileLedgerEntry;
import io.casehub.aml.ledger.AmlComplianceReviewLedgerEntry;
import io.casehub.aml.ledger.AmlSarOfficerReviewedLedgerEntry;
import io.casehub.aml.trust.AmlAttestationReconciler;
import io.casehub.aml.trust.AmlTrustAttestationRepository;
import io.casehub.aml.trust.AmlTrustRoutingAttestation;
import io.casehub.aml.trust.AmlWorkerDecisionRepository;
import io.casehub.blocks.routing.RequirementStatus;
import io.casehub.blocks.routing.RoutingDecisionRecord;
import io.casehub.blocks.routing.TrustRoutingRequirement;
import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.model.WorkerDecisionEntry;
import io.casehub.ledger.runtime.config.LedgerConfig;
import io.casehub.ledger.runtime.repository.ErasureReceiptRepository;
import io.casehub.ledger.runtime.service.LedgerVerificationService;
import io.casehub.ledger.runtime.service.model.InclusionProof;
import io.casehub.work.runtime.model.WorkItemEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Layer 7: assembles compliance evidence for an AML investigation case.
 *
 * <p>Collects data from the Merkle-chained ledger (Layer 4/8), WorkItem SLA (Layer 2),
 * trust routing attestations (Layer 6/7), and GDPR erasure capability (Layer 4)
 * to produce a {@link ComplianceEvidence} record that maps each FinCEN/FATF
 * requirement to its current status.
 */
@ApplicationScoped
public class AmlComplianceEvidenceService {

    private final LedgerEntryRepository ledgerRepo;
    private final LedgerVerificationService verificationService;
    private final AmlTrustAttestationRepository attestationRepo;
    private final AmlWorkerDecisionRepository workerDecisionRepo;
    private final EntityManager em;
    private final AmlAttestationReconciler reconciler;
    private final LedgerConfig ledgerConfig;
    private final ErasureReceiptRepository erasureReceiptRepo;

    @Inject
    public AmlComplianceEvidenceService(
            LedgerEntryRepository ledgerRepo,
            LedgerVerificationService verificationService,
            AmlTrustAttestationRepository attestationRepo,
            AmlWorkerDecisionRepository workerDecisionRepo,
            EntityManager em,
            AmlAttestationReconciler reconciler,
            LedgerConfig ledgerConfig,
            ErasureReceiptRepository erasureReceiptRepo) {
        this.ledgerRepo = ledgerRepo;
        this.verificationService = verificationService;
        this.attestationRepo = attestationRepo;
        this.workerDecisionRepo = workerDecisionRepo;
        this.em = em;
        this.reconciler = reconciler;
        this.ledgerConfig = ledgerConfig;
        this.erasureReceiptRepo = erasureReceiptRepo;
    }

    /**
     * Returns compliance evidence for the given case, or empty if no AML ledger entries exist.
     */
    public Optional<ComplianceEvidence> findEvidence(UUID caseId) {
        List<LedgerEntry>                      all                  = ledgerRepo.findBySubjectId(caseId, io.casehub.platform.api.identity.TenancyConstants.DEFAULT_TENANT_ID);
        List<AmlCaseOpenedLedgerEntry>         caseEntries          = filterCaseOpened(all);
        List<AmlComplianceReviewLedgerEntry>   reviewEntries        = filterComplianceReview(all);
        List<AmlSarOfficerReviewedLedgerEntry> officerReviewEntries = filterSarOfficerReviewed(all);
        if (caseEntries.isEmpty() && reviewEntries.isEmpty() && officerReviewEntries.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(build(caseId, caseEntries, reviewEntries, officerReviewEntries, all));}

    /**
     * Assembles full compliance evidence for the given case.
     * Package-private for direct unit test access.
     */
    ComplianceEvidence assembleEvidence(UUID caseId) {
        List<LedgerEntry> all = ledgerRepo.findBySubjectId(caseId, io.casehub.platform.api.identity.TenancyConstants.DEFAULT_TENANT_ID);
        return build(caseId,
                     filterCaseOpened(all), filterComplianceReview(all),
                     filterSarOfficerReviewed(all), all);}

    private ComplianceEvidence build(UUID caseId,
                                     List<AmlCaseOpenedLedgerEntry> caseEntries,
                                     List<AmlComplianceReviewLedgerEntry> reviewEntries,
                                     List<AmlSarOfficerReviewedLedgerEntry> officerReviewEntries,
                                     List<LedgerEntry> all) {
        return new ComplianceEvidence(
                caseId,
                Instant.now(),
                buildAuditChain(caseId, caseEntries, reviewEntries, officerReviewEntries),
                buildSla(reviewEntries),
                buildTrustRouting(caseId),
                buildGdprErasure(),
                buildArt22DecisionRecord(all),
                null
        );
    }

    // -- Audit chain -----------------------------------------------------------

    private AuditChainRequirement buildAuditChain(UUID caseId,
            List<AmlCaseOpenedLedgerEntry> caseEntries,
            List<AmlComplianceReviewLedgerEntry> reviewEntries,
            List<AmlSarOfficerReviewedLedgerEntry> officerReviewEntries) {
        if (caseEntries.isEmpty() && reviewEntries.isEmpty() && officerReviewEntries.isEmpty()) {
            return new AuditChainRequirement(
                    AuditChainRequirement.REQUIREMENT_ID,
                    AuditChainRequirement.CITATION,
                    AuditChainRequirement.MECHANISM,
                    RequirementStatus.GAP, null, false, List.of());
        }

        boolean chainVerified = false;
        String treeRoot = null;
        try {
            chainVerified = verificationService.verify(caseId, io.casehub.platform.api.identity.TenancyConstants.DEFAULT_TENANT_ID);
            treeRoot = verificationService.treeRoot(caseId, io.casehub.platform.api.identity.TenancyConstants.DEFAULT_TENANT_ID);
        } catch (IllegalStateException ignored) {
            // No Merkle frontier — hash-chain disabled (tests) or not yet built
        }

        List<LedgerEventRecord> events = new ArrayList<>();
        for (AmlCaseOpenedLedgerEntry entry : caseEntries) {
            AmlInclusionProof proof = buildInclusionProof(entry.id);
            events.add(new LedgerEventRecord(
                    entry.id, "CASE_OPENED", entry.actorId, entry.actorRole,
                    entry.occurredAt, entry.causedByEntryId, entry.digest, proof));
        }
        for (AmlComplianceReviewLedgerEntry entry : reviewEntries) {
            AmlInclusionProof proof = buildInclusionProof(entry.id);
            events.add(new LedgerEventRecord(
                    entry.id, "COMPLIANCE_REVIEW_OPENED", entry.actorId, entry.actorRole,
                    entry.occurredAt, entry.causedByEntryId, entry.digest, proof));
        }
        for (AmlSarOfficerReviewedLedgerEntry entry : officerReviewEntries) {
            AmlInclusionProof proof = buildInclusionProof(entry.id);
            events.add(new LedgerEventRecord(
                    entry.id, "SAR_OFFICER_REVIEWED", entry.actorId, entry.actorRole,
                    entry.occurredAt, entry.causedByEntryId, entry.digest, proof));
        }

        boolean allLinked = reviewEntries.stream().allMatch(e -> e.causedByEntryId != null)
                && officerReviewEntries.stream().allMatch(e -> e.causedByEntryId != null);

        RequirementStatus status;
        if (chainVerified && allLinked && !officerReviewEntries.isEmpty()) {
            status = RequirementStatus.CLOSED;
        } else if (!caseEntries.isEmpty() || !reviewEntries.isEmpty() || !officerReviewEntries.isEmpty()) {
            status = RequirementStatus.PARTIAL;
        } else {
            status = RequirementStatus.GAP;
        }

        return new AuditChainRequirement(
                AuditChainRequirement.REQUIREMENT_ID,
                AuditChainRequirement.CITATION,
                AuditChainRequirement.MECHANISM,
                status, treeRoot, chainVerified, events);
    }

    private AmlInclusionProof buildInclusionProof(UUID entryId) {
        try {
            InclusionProof proof = verificationService.inclusionProof(entryId, io.casehub.platform.api.identity.TenancyConstants.DEFAULT_TENANT_ID);
            List<AmlProofStep> steps = proof.siblings().stream()
                    .map(s -> new AmlProofStep(s.hash(), s.side().name()))
                    .toList();
            return new AmlInclusionProof(
                    proof.entryIndex(), proof.treeSize(),
                    proof.leafHash(), steps, proof.treeRoot());
        } catch (Exception ignored) {
            return null;
        }
    }

    // -- SLA -------------------------------------------------------------------

    private SlaRequirement buildSla(List<AmlComplianceReviewLedgerEntry> reviewEntries) {
        Optional<AmlComplianceReviewLedgerEntry> reviewEntry = reviewEntries.stream().findFirst();

        if (reviewEntry.isEmpty()) {
            return new SlaRequirement(
                    SlaRequirement.REQUIREMENT_ID,
                    SlaRequirement.CITATION,
                    SlaRequirement.MECHANISM,
                    RequirementStatus.GAP,
                    null, null, null, false, List.of(),
                    SlaRequirement.ESCALATION_POLICY);
        }

        UUID taskId;
        try {
            taskId = UUID.fromString(reviewEntry.get().taskId);
        } catch (IllegalArgumentException e) {
            return new SlaRequirement(
                    SlaRequirement.REQUIREMENT_ID,
                    SlaRequirement.CITATION,
                    SlaRequirement.MECHANISM,
                    RequirementStatus.PARTIAL,
                    null, null, null, false, List.of(),
                    SlaRequirement.ESCALATION_POLICY);
        }

        WorkItemEntity wi = em.find(WorkItemEntity.class, taskId);
        if (wi == null) {
            return new SlaRequirement(
                    SlaRequirement.REQUIREMENT_ID,
                    SlaRequirement.CITATION,
                    SlaRequirement.MECHANISM,
                    RequirementStatus.PARTIAL,
                    taskId, null, null, false, List.of(),
                    SlaRequirement.ESCALATION_POLICY);
        }

        Instant claimDeadline = wi.claimDeadline;
        Instant completedAt = wi.completedAt;
        boolean slaMet = completedAt != null && claimDeadline != null
                && completedAt.isBefore(claimDeadline);

        List<String> candidateGroups = wi.candidateGroups != null
                ? Arrays.asList(wi.candidateGroups.split(","))
                : List.of();

        RequirementStatus status;
        if (claimDeadline == null) {
            status = RequirementStatus.PARTIAL;     // WorkItem found but deadline unset
        } else if (completedAt == null && Instant.now().isAfter(claimDeadline)) {
            status = RequirementStatus.BREACHED;    // deadline passed, officer hasn't acted
        } else if (completedAt != null && !slaMet) {
            status = RequirementStatus.BREACHED;    // completed after deadline
        } else if (completedAt == null) {
            status = RequirementStatus.PARTIAL;     // within deadline, officer hasn't acted yet
        } else {
            status = RequirementStatus.CLOSED;      // completed before deadline
        }

        return new SlaRequirement(
                SlaRequirement.REQUIREMENT_ID,
                SlaRequirement.CITATION,
                SlaRequirement.MECHANISM,
                status, taskId, claimDeadline, completedAt, slaMet, candidateGroups,
                SlaRequirement.ESCALATION_POLICY);
    }

    // -- Trust routing ---------------------------------------------------------

    private static final String TRUST_ROUTING_REQUIREMENT_ID = "FATF-R20-TRUST-ROUTING";
    private static final String TRUST_ROUTING_CITATION =
        "FATF Recommendation 20 — Experienced analysts on complex cases";
    private static final String TRUST_ROUTING_MECHANISM =
        "Layer 6: TrustWeightedAgentStrategy reads AmlTrustRoutingPolicyProvider thresholds. " +
        "Layer 7: AmlTrustRoutingAttestation captures trustScoreAtRouting at WorkerDecisionEvent " +
        "time before TrustScoreCache can drift. Workaround for casehubio/engine#403.";

    private TrustRoutingRequirement buildTrustRouting(UUID caseId) {
        List<WorkerDecisionEntry> dispatched = workerDecisionRepo.findAllByCaseId(caseId);
        Set<String> dispatchedCaps = dispatched.stream()
                .map(w -> w.capabilityTag)
                .collect(Collectors.toSet());

        List<AmlTrustRoutingAttestation> raw = attestationRepo.findByInvestigationCaseId(caseId);
        List<AmlTrustRoutingAttestation> attestations = reconciler.reconcileIfNeeded(caseId, dispatched, raw);
        Set<String> attestedCaps = attestations.stream()
                .map(a -> a.capabilityTag)
                .collect(Collectors.toSet());

        List<RoutingDecisionRecord> decisions = attestations.stream()
                .map(a -> new RoutingDecisionRecord(
                        a.capabilityTag, a.selectedWorkerId,
                        a.trustScoreAtRouting, a.thresholdApplied, a.id))
                .toList();

        RequirementStatus status;
        if (dispatchedCaps.isEmpty()) {
            status = RequirementStatus.GAP;
        } else if (attestations.stream().allMatch(a -> !a.observerFailed && !a.reconstructed)
                && attestedCaps.containsAll(dispatchedCaps)) {
            status = RequirementStatus.CLOSED;
        } else {
            status = RequirementStatus.PARTIAL;
        }

        return new TrustRoutingRequirement(
                TRUST_ROUTING_REQUIREMENT_ID,
                TRUST_ROUTING_CITATION,
                TRUST_ROUTING_MECHANISM,
                status, decisions);
    }

    // -- GDPR erasure ----------------------------------------------------------

    private GdprErasureRequirement buildGdprErasure() {
        boolean tokenisationEnabled = ledgerConfig.identity().tokenisation().enabled();
        boolean receiptEnabled = ledgerConfig.erasureReceipt().enabled();

        long receiptCount = 0L;
        try {
            receiptCount = erasureReceiptRepo.countByTenant(
                    io.casehub.platform.api.identity.TenancyConstants.DEFAULT_TENANT_ID);
        } catch (Exception ignored) {
        }

        RequirementStatus status;
        if (tokenisationEnabled && receiptEnabled) {
            status = RequirementStatus.CLOSED;
        } else if (tokenisationEnabled || receiptEnabled) {
            status = RequirementStatus.PARTIAL;
        } else {
            status = RequirementStatus.GAP;
        }

        return new GdprErasureRequirement(
                GdprErasureRequirement.REQUIREMENT_ID,
                GdprErasureRequirement.CITATION,
                GdprErasureRequirement.MECHANISM,
                status, tokenisationEnabled, receiptEnabled, receiptCount,
                GdprErasureRequirement.ERASURE_ENDPOINT,
                GdprErasureRequirement.RETENTION_CITATION,
                "ADR-0004");
    }

    // -- Internal helpers ------------------------------------------------------


    private Art22DecisionRecordRequirement buildArt22DecisionRecord(List<LedgerEntry> all) {
        List<AmlCaseProfileLedgerEntry> profileEntries = all.stream()
                                                            .filter(AmlCaseProfileLedgerEntry.class::isInstance)
                                                            .map(AmlCaseProfileLedgerEntry.class::cast)
                                                            .toList();

        if (profileEntries.isEmpty()) {
            return new Art22DecisionRecordRequirement(
                    Art22DecisionRecordRequirement.REQUIREMENT_ID,
                    Art22DecisionRecordRequirement.CITATION,
                    Art22DecisionRecordRequirement.MECHANISM,
                    RequirementStatus.GAP, List.of());
        }

        List<Art22DecisionRecord> records     = new ArrayList<>();
        boolean                   allComplete = true;

        for (AmlCaseProfileLedgerEntry entry : profileEntries) {
            Optional<io.casehub.ledger.api.model.supplement.ComplianceSupplement> supplement = entry.compliance();
            if (supplement.isPresent()) {
                io.casehub.ledger.api.model.supplement.ComplianceSupplement s = supplement.get();
                records.add(new Art22DecisionRecord(
                        entry.id, s.algorithmRef, s.confidenceScore,
                        s.rationale, Boolean.TRUE.equals(s.humanOverrideAvailable),
                        s.contestationUri, s.decisionContext != null));
                if (s.algorithmRef == null || s.humanOverrideAvailable == null) {
                    allComplete = false;
                }
            } else {
                allComplete = false;
            }
        }

        RequirementStatus status;
        if (!records.isEmpty() && allComplete) {
            status = RequirementStatus.CLOSED;
        } else if (!records.isEmpty()) {
            status = RequirementStatus.PARTIAL;
        } else {
            status = RequirementStatus.GAP;
        }

        return new Art22DecisionRecordRequirement(
                Art22DecisionRecordRequirement.REQUIREMENT_ID,
                Art22DecisionRecordRequirement.CITATION,
                Art22DecisionRecordRequirement.MECHANISM,
                status, records);
    }

    private List<AmlCaseOpenedLedgerEntry> filterCaseOpened(List<LedgerEntry> entries) {
        return entries.stream()
                .filter(AmlCaseOpenedLedgerEntry.class::isInstance)
                .map(AmlCaseOpenedLedgerEntry.class::cast)
                .toList();
    }

    private List<AmlComplianceReviewLedgerEntry> filterComplianceReview(List<LedgerEntry> entries) {
        return entries.stream()
                .filter(AmlComplianceReviewLedgerEntry.class::isInstance)
                .map(AmlComplianceReviewLedgerEntry.class::cast)
                .toList();
    }

    private List<AmlSarOfficerReviewedLedgerEntry> filterSarOfficerReviewed(List<LedgerEntry> entries) {
        return entries.stream()
                .filter(AmlSarOfficerReviewedLedgerEntry.class::isInstance)
                .map(AmlSarOfficerReviewedLedgerEntry.class::cast)
                .toList();
    }
}
