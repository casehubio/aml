package io.casehub.aml.compliance;

import io.casehub.aml.ledger.AmlCaseOpenedLedgerEntry;
import io.casehub.aml.ledger.AmlComplianceReviewLedgerEntry;
import io.casehub.aml.ledger.AmlSarOfficerReviewedLedgerEntry;
import io.casehub.aml.trust.AmlAttestationReconciler;
import io.casehub.aml.trust.AmlTrustAttestationRepository;
import io.casehub.aml.trust.AmlTrustRoutingAttestation;
import io.casehub.aml.trust.AmlWorkerDecisionRepository;
import io.casehub.blocks.routing.RequirementStatus;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.model.WorkerDecisionEntry;
import io.casehub.ledger.runtime.config.LedgerConfig;
import io.casehub.ledger.runtime.repository.ErasureReceiptRepository;
import io.casehub.ledger.runtime.service.LedgerVerificationService;
import io.casehub.ledger.runtime.service.model.InclusionProof;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.work.api.WorkItemStatus;
import io.casehub.work.runtime.model.WorkItemEntity;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class AmlComplianceEvidenceServiceTest {

    @Mock LedgerEntryRepository ledgerRepo;
    @Mock LedgerVerificationService verificationService;
    @Mock AmlTrustAttestationRepository attestationRepo;
    @Mock AmlWorkerDecisionRepository workerDecisionRepo;
    @Mock EntityManager em;
    @Mock AmlAttestationReconciler mockReconciler;
    @Mock LedgerConfig ledgerConfig;
    @Mock LedgerConfig.IdentityConfig identityConfig;
    @Mock LedgerConfig.IdentityConfig.TokenisationConfig tokenisationConfig;
    @Mock LedgerConfig.ErasureReceiptConfig erasureReceiptConfig;
    @Mock ErasureReceiptRepository erasureReceiptRepo;

    AmlComplianceEvidenceService service;

    UUID caseId = UUID.randomUUID();
    UUID caseOpenedId = UUID.randomUUID();
    UUID reviewOpenedId = UUID.randomUUID();
    UUID taskId = UUID.randomUUID();
    String treeRoot = "sha256:abc123";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(ledgerConfig.identity()).thenReturn(identityConfig);
        when(identityConfig.tokenisation()).thenReturn(tokenisationConfig);
        when(ledgerConfig.erasureReceipt()).thenReturn(erasureReceiptConfig);
        when(tokenisationConfig.enabled()).thenReturn(true);
        when(erasureReceiptConfig.enabled()).thenReturn(true);
        when(erasureReceiptRepo.countByTenant(any())).thenReturn(0L);
        service = new AmlComplianceEvidenceService(
                ledgerRepo, verificationService, attestationRepo,
                workerDecisionRepo, em, mockReconciler,
                ledgerConfig, erasureReceiptRepo);
    }

    @Test
    void assembleEvidence_happyPath_allRequirementsClosed() {
        var opened = caseOpenedEntry(caseId, caseOpenedId);
        var review = reviewOpenedEntry(caseId, reviewOpenedId, taskId, caseOpenedId);
        when(ledgerRepo.findBySubjectId(eq(caseId), any())).thenReturn(List.of(opened, review));
        when(verificationService.verify(eq(caseId), any())).thenReturn(true);
        when(verificationService.treeRoot(eq(caseId), any())).thenReturn(treeRoot);
        when(verificationService.inclusionProof(any(), any())).thenReturn(stubProof());
        WorkItemEntity wi = workItem(taskId, Instant.now().plus(30, ChronoUnit.DAYS), null);
        when(em.find(eq(WorkItemEntity.class), eq(taskId))).thenReturn(wi);
        var att1 = attestation(caseId, "entity-resolution", "agent-A", 0.8, 0.70);
        var att2 = attestation(caseId, "sar-drafting", "agent-B", 0.9, 0.75);
        when(attestationRepo.findByInvestigationCaseId(caseId)).thenReturn(List.of(att1, att2));
        when(mockReconciler.reconcileIfNeeded(eq(caseId), any(), any())).thenReturn(List.of(att1, att2));
        var wd1 = workerDecision(caseId, "entity-resolution", "agent-A");
        var wd2 = workerDecision(caseId, "sar-drafting", "agent-B");
        when(workerDecisionRepo.findAllByCaseId(caseId)).thenReturn(List.of(wd1, wd2));

        ComplianceEvidence evidence = service.assembleEvidence(caseId);

        assertEquals(caseId, evidence.caseId());
        assertNotNull(evidence.generatedAt());
        assertNull(evidence.signature());
        // No officer review entry — CLOSED requires ≥1 officerReview; PARTIAL without it
        assertEquals(RequirementStatus.PARTIAL, evidence.auditChain().status());
        assertTrue(evidence.auditChain().chainVerified());
        assertEquals(treeRoot, evidence.auditChain().treeRoot());
        assertEquals(2, evidence.auditChain().events().size());
        assertNull(evidence.auditChain().events().get(0).causedByEntryId());
        assertEquals(caseOpenedId, evidence.auditChain().events().get(1).causedByEntryId());
        // WorkItem open (completedAt=null) with deadline 30 days away — officer hasn't acted yet.
        // slaMet=false (not completed), status=PARTIAL (mechanism present, evidence incomplete).
        assertFalse(evidence.sla().slaMet());
        assertEquals(RequirementStatus.PARTIAL, evidence.sla().status());
        assertNotNull(evidence.sla().workItemId());
        assertEquals(RequirementStatus.CLOSED, evidence.trustRouting().status());
        assertEquals(2, evidence.trustRouting().decisions().size());
        assertEquals(RequirementStatus.CLOSED, evidence.gdprErasure().status());
        assertTrue(evidence.gdprErasure().tokenisationEnabled());
        assertTrue(evidence.gdprErasure().erasureReceiptEnabled());
    }

    @Test
    void assembleEvidence_chainVerifiedFalse_auditChainPartial() {
        var opened = caseOpenedEntry(caseId, caseOpenedId);
        var review = reviewOpenedEntry(caseId, reviewOpenedId, taskId, caseOpenedId);
        when(ledgerRepo.findBySubjectId(eq(caseId), any())).thenReturn(List.of(opened, review));
        when(verificationService.verify(eq(caseId), any())).thenReturn(false);
        when(verificationService.treeRoot(eq(caseId), any())).thenReturn(treeRoot);
        when(verificationService.inclusionProof(any(), any())).thenReturn(stubProof());
        when(em.find(eq(WorkItemEntity.class), eq(taskId))).thenReturn(workItem(taskId, Instant.now().plus(30, ChronoUnit.DAYS), null));
        when(attestationRepo.findByInvestigationCaseId(caseId)).thenReturn(List.of());
        when(mockReconciler.reconcileIfNeeded(eq(caseId), any(), any())).thenReturn(List.of());
        when(workerDecisionRepo.findAllByCaseId(caseId)).thenReturn(List.of());

        assertEquals(RequirementStatus.PARTIAL, service.assembleEvidence(caseId).auditChain().status());
    }

    @Test
    void assembleEvidence_slaBreached_statusBreached() {
        var opened = caseOpenedEntry(caseId, caseOpenedId);
        var review = reviewOpenedEntry(caseId, reviewOpenedId, taskId, caseOpenedId);
        when(ledgerRepo.findBySubjectId(eq(caseId), any())).thenReturn(List.of(opened, review));
        when(verificationService.verify(eq(caseId), any())).thenReturn(true);
        when(verificationService.treeRoot(eq(caseId), any())).thenReturn(treeRoot);
        when(verificationService.inclusionProof(any(), any())).thenReturn(stubProof());
        when(attestationRepo.findByInvestigationCaseId(caseId)).thenReturn(List.of());
        when(mockReconciler.reconcileIfNeeded(eq(caseId), any(), any())).thenReturn(List.of());
        when(workerDecisionRepo.findAllByCaseId(caseId)).thenReturn(List.of());
        Instant deadline = Instant.now().minus(1, ChronoUnit.DAYS);
        when(em.find(eq(WorkItemEntity.class), eq(taskId))).thenReturn(workItem(taskId, deadline, null));

        assertEquals(RequirementStatus.BREACHED, service.assembleEvidence(caseId).sla().status());
    }

    @Test
    void assembleEvidence_partialAttestations_trustRoutingPartial() {
        var opened = caseOpenedEntry(caseId, caseOpenedId);
        var review = reviewOpenedEntry(caseId, reviewOpenedId, taskId, caseOpenedId);
        when(ledgerRepo.findBySubjectId(eq(caseId), any())).thenReturn(List.of(opened, review));
        when(verificationService.verify(eq(caseId), any())).thenReturn(true);
        when(verificationService.treeRoot(eq(caseId), any())).thenReturn(treeRoot);
        when(verificationService.inclusionProof(any(), any())).thenReturn(stubProof());
        when(em.find(eq(WorkItemEntity.class), eq(taskId))).thenReturn(workItem(taskId, Instant.now().plus(30, ChronoUnit.DAYS), null));
        var partialAtt = attestation(caseId, "entity-resolution", "agent-A", 0.8, 0.70);
        when(attestationRepo.findByInvestigationCaseId(caseId)).thenReturn(List.of(partialAtt));
        when(mockReconciler.reconcileIfNeeded(eq(caseId), any(), any())).thenReturn(List.of(partialAtt));
        when(workerDecisionRepo.findAllByCaseId(caseId)).thenReturn(List.of(
            workerDecision(caseId, "entity-resolution", "agent-A"),
            workerDecision(caseId, "sar-drafting", "agent-B")));

        assertEquals(RequirementStatus.PARTIAL, service.assembleEvidence(caseId).trustRouting().status());
    }

    @Test
    void findEvidence_noLedgerEntries_returnsEmpty() {
        when(ledgerRepo.findBySubjectId(eq(caseId), any())).thenReturn(List.of());
        assertTrue(service.findEvidence(caseId).isEmpty());
    }

    // -- Helpers ---------------------------------------------------------------

    private AmlCaseOpenedLedgerEntry caseOpenedEntry(UUID caseId, UUID entryId) {
        var e = new AmlCaseOpenedLedgerEntry();
        e.id = entryId; e.subjectId = caseId; e.sequenceNumber = 1;
        e.transactionId = "TXN-001"; e.originAccountId = "ACC-A"; e.destinationAccountId = "ACC-B";
        e.entryType = LedgerEntryType.EVENT; e.actorId = "aml-orchestrator";
        e.actorType = ActorType.SYSTEM; e.actorRole = "AmlInvestigationOrchestrator";
        e.occurredAt = Instant.now().minus(5, ChronoUnit.MINUTES);
        e.digest = "sha256:abc"; e.causedByEntryId = null;
        return e;
    }

    private AmlComplianceReviewLedgerEntry reviewOpenedEntry(UUID caseId, UUID entryId,
            UUID taskId, UUID causedBy) {
        var e = new AmlComplianceReviewLedgerEntry();
        e.id = entryId; e.subjectId = caseId; e.sequenceNumber = 2;
        e.taskId = taskId.toString();
        e.entryType = LedgerEntryType.EVENT; e.actorId = "aml-orchestrator";
        e.actorType = ActorType.SYSTEM; e.actorRole = "AmlInvestigationOrchestrator";
        e.occurredAt = Instant.now(); e.digest = "sha256:def"; e.causedByEntryId = causedBy;
        return e;
    }

    private WorkItemEntity workItem(UUID taskId, Instant deadline, Instant completedAt) {
        var wi = new WorkItemEntity();
        wi.id = taskId;
        wi.status = completedAt != null ? WorkItemStatus.COMPLETED : WorkItemStatus.PENDING;
        wi.claimDeadline = deadline;
        wi.completedAt = completedAt;
        wi.candidateGroups = "compliance-officers";
        return wi;
    }

    private AmlTrustRoutingAttestation attestation(UUID caseId, String cap, String worker,
            double score, double threshold) {
        var a = new AmlTrustRoutingAttestation();
        a.id = UUID.randomUUID(); a.investigationCaseId = caseId;
        a.capabilityTag = cap; a.selectedWorkerId = worker;
        a.trustScoreAtRouting = score; a.thresholdApplied = threshold;
        a.occurredAt = Instant.now();
        return a;
    }

    private WorkerDecisionEntry workerDecision(UUID caseId, String cap, String worker) {
        var wd = new WorkerDecisionEntry();
        wd.caseId = caseId; wd.capabilityTag = cap; wd.workerId = worker;
        return wd;
    }

    private InclusionProof stubProof() {
        return new InclusionProof(UUID.randomUUID(), 0, 2, "sha256:leaf", List.of(), "sha256:root");
    }

    private AmlSarOfficerReviewedLedgerEntry officerReviewEntry(UUID caseId, UUID entryId, UUID causedBy) {
        AmlSarOfficerReviewedLedgerEntry e = new AmlSarOfficerReviewedLedgerEntry();
        e.id = entryId;
        e.subjectId = caseId;
        e.sequenceNumber = 3;
        e.actorId = "compliance-officer-001";
        e.actorRole = "ComplianceOfficer";
        e.actorType = ActorType.HUMAN;
        e.occurredAt = Instant.now();
        e.causedByEntryId = causedBy;
        e.reviewDecision = "APPROVED";
        e.entryType = LedgerEntryType.EVENT;
        return e;
    }

    @Test
    void assembleEvidence_withOfficerReview_auditChainClosed() {
        var opened = caseOpenedEntry(caseId, caseOpenedId);
        var review = reviewOpenedEntry(caseId, reviewOpenedId, taskId, caseOpenedId);
        var officerReview = officerReviewEntry(caseId, UUID.randomUUID(), reviewOpenedId);
        when(ledgerRepo.findBySubjectId(eq(caseId), any())).thenReturn(List.of(opened, review, officerReview));
        when(verificationService.verify(eq(caseId), any())).thenReturn(true);
        when(verificationService.treeRoot(eq(caseId), any())).thenReturn(treeRoot);
        when(verificationService.inclusionProof(any(), any())).thenReturn(stubProof());
        when(em.find(eq(WorkItemEntity.class), eq(taskId)))
            .thenReturn(workItem(taskId, Instant.now().plus(30, ChronoUnit.DAYS), null));
        when(attestationRepo.findByInvestigationCaseId(caseId)).thenReturn(List.of());
        when(workerDecisionRepo.findAllByCaseId(caseId)).thenReturn(List.of());
        when(mockReconciler.reconcileIfNeeded(eq(caseId), any(), any())).thenReturn(List.of());

        ComplianceEvidence evidence = service.assembleEvidence(caseId);

        assertEquals(RequirementStatus.CLOSED, evidence.auditChain().status());
        assertEquals(3, evidence.auditChain().events().size());
        assertEquals("SAR_OFFICER_REVIEWED", evidence.auditChain().events().get(2).eventType());
        assertEquals("compliance-officer-001", evidence.auditChain().events().get(2).actorId());
    }

    @Test
    void assembleEvidence_observerFailedAttestation_trustRoutingPartial() {
        var opened = caseOpenedEntry(caseId, caseOpenedId);
        var review = reviewOpenedEntry(caseId, reviewOpenedId, taskId, caseOpenedId);
        when(ledgerRepo.findBySubjectId(eq(caseId), any())).thenReturn(List.of(opened, review));
        when(verificationService.verify(eq(caseId), any())).thenReturn(false);
        when(verificationService.treeRoot(eq(caseId), any())).thenReturn(treeRoot);
        when(verificationService.inclusionProof(any(), any())).thenReturn(stubProof());
        when(em.find(eq(WorkItemEntity.class), eq(taskId)))
            .thenReturn(workItem(taskId, Instant.now().plus(30, ChronoUnit.DAYS), null));
        var failureAtt = attestation(caseId, "entity-resolution", "agent-A", 0.8, 0.70);
        failureAtt.observerFailed = true;
        when(attestationRepo.findByInvestigationCaseId(caseId)).thenReturn(List.of(failureAtt));
        when(mockReconciler.reconcileIfNeeded(eq(caseId), any(), any())).thenReturn(List.of(failureAtt));
        when(workerDecisionRepo.findAllByCaseId(caseId)).thenReturn(List.of(
            workerDecision(caseId, "entity-resolution", "agent-A")));

        assertEquals(RequirementStatus.PARTIAL, service.assembleEvidence(caseId).trustRouting().status());
    }

    @Test
    void assembleEvidence_reconstructedAttestation_trustRoutingPartial() {
        var opened = caseOpenedEntry(caseId, caseOpenedId);
        when(ledgerRepo.findBySubjectId(eq(caseId), any())).thenReturn(List.of(opened));
        when(verificationService.verify(eq(caseId), any())).thenReturn(false);
        when(verificationService.treeRoot(eq(caseId), any())).thenReturn(treeRoot);
        when(verificationService.inclusionProof(any(), any())).thenReturn(stubProof());
        when(em.find(eq(WorkItemEntity.class), any())).thenReturn(null);
        var recon = attestation(caseId, "entity-resolution", "agent-A", 0.8, 0.70);
        recon.reconstructed = true;
        when(attestationRepo.findByInvestigationCaseId(caseId)).thenReturn(List.of(recon));
        when(mockReconciler.reconcileIfNeeded(eq(caseId), any(), any())).thenReturn(List.of(recon));
        when(workerDecisionRepo.findAllByCaseId(caseId)).thenReturn(List.of(
            workerDecision(caseId, "entity-resolution", "agent-A")));

        assertEquals(RequirementStatus.PARTIAL, service.assembleEvidence(caseId).trustRouting().status());
    }

    @Test
    void gdprErasure_both_enabled_returns_closed() {
        when(tokenisationConfig.enabled()).thenReturn(true);
        when(erasureReceiptConfig.enabled()).thenReturn(true);
        when(erasureReceiptRepo.countByTenant(any())).thenReturn(3L);
        setupMinimalLedgerEntries();

        ComplianceEvidence evidence = service.assembleEvidence(caseId);

        assertEquals(RequirementStatus.CLOSED, evidence.gdprErasure().status());
        assertTrue(evidence.gdprErasure().tokenisationEnabled());
        assertTrue(evidence.gdprErasure().erasureReceiptEnabled());
        assertEquals(3L, evidence.gdprErasure().erasureReceiptCount());
    }

    @Test
    void gdprErasure_tokenisation_only_returns_partial() {
        when(tokenisationConfig.enabled()).thenReturn(true);
        when(erasureReceiptConfig.enabled()).thenReturn(false);
        when(erasureReceiptRepo.countByTenant(any())).thenReturn(0L);
        setupMinimalLedgerEntries();

        ComplianceEvidence evidence = service.assembleEvidence(caseId);

        assertEquals(RequirementStatus.PARTIAL, evidence.gdprErasure().status());
        assertTrue(evidence.gdprErasure().tokenisationEnabled());
        assertFalse(evidence.gdprErasure().erasureReceiptEnabled());
    }

    @Test
    void gdprErasure_neither_enabled_returns_gap() {
        when(tokenisationConfig.enabled()).thenReturn(false);
        when(erasureReceiptConfig.enabled()).thenReturn(false);
        when(erasureReceiptRepo.countByTenant(any())).thenReturn(0L);
        setupMinimalLedgerEntries();

        ComplianceEvidence evidence = service.assembleEvidence(caseId);

        assertEquals(RequirementStatus.GAP, evidence.gdprErasure().status());
    }

    @Test
    void gdprErasure_db_failure_degrades_gracefully() {
        when(tokenisationConfig.enabled()).thenReturn(true);
        when(erasureReceiptConfig.enabled()).thenReturn(true);
        when(erasureReceiptRepo.countByTenant(any())).thenThrow(new RuntimeException("DB down"));
        setupMinimalLedgerEntries();

        ComplianceEvidence evidence = service.assembleEvidence(caseId);

        assertEquals(RequirementStatus.CLOSED, evidence.gdprErasure().status());
        assertEquals(0L, evidence.gdprErasure().erasureReceiptCount());
    }


    @Test
    void assembleEvidence_withArt22Supplement_statusClosed() {
        setupMinimalLedgerEntries();
        var profileEntry = profileEntryWithSupplement(caseId);
        when(ledgerRepo.findBySubjectId(eq(caseId), any()))
                .thenReturn(List.of(
                        caseOpenedEntry(caseId, caseOpenedId),
                        reviewOpenedEntry(caseId, reviewOpenedId, taskId, caseOpenedId),
                        profileEntry));
        when(verificationService.inclusionProof(any(), any())).thenReturn(stubProof());
        when(attestationRepo.findByInvestigationCaseId(caseId)).thenReturn(List.of());
        when(workerDecisionRepo.findAllByCaseId(caseId)).thenReturn(List.of());
        when(mockReconciler.reconcileIfNeeded(any(), any(), any())).thenReturn(List.of());

        ComplianceEvidence evidence = service.assembleEvidence(caseId);

        assertNotNull(evidence.art22DecisionRecord());
        assertEquals(RequirementStatus.CLOSED, evidence.art22DecisionRecord().status());
        assertEquals(1, evidence.art22DecisionRecord().decisions().size());
        assertTrue(evidence.art22DecisionRecord().decisions().get(0).humanOverrideAvailable());
    }

    @Test
    void assembleEvidence_noProfileEntry_art22Gap() {
        setupMinimalLedgerEntries();
        when(verificationService.inclusionProof(any(), any())).thenReturn(stubProof());
        when(attestationRepo.findByInvestigationCaseId(caseId)).thenReturn(List.of());
        when(workerDecisionRepo.findAllByCaseId(caseId)).thenReturn(List.of());
        when(mockReconciler.reconcileIfNeeded(any(), any(), any())).thenReturn(List.of());

        ComplianceEvidence evidence = service.assembleEvidence(caseId);

        assertNotNull(evidence.art22DecisionRecord());
        assertEquals(RequirementStatus.GAP, evidence.art22DecisionRecord().status());
        assertTrue(evidence.art22DecisionRecord().decisions().isEmpty());
    }

    @Test
    void assembleEvidence_profileWithoutSupplement_art22Gap() {
        setupMinimalLedgerEntries();
        var profileEntry = new io.casehub.aml.ledger.AmlCaseProfileLedgerEntry();
        profileEntry.subjectId          = caseId;
        profileEntry.id                 = UUID.randomUUID();
        profileEntry.outcome            = "SAR_WARRANTED";
        profileEntry.flagReason         = "HIGH_RISK_JURISDICTION";
        profileEntry.transactionAmount  = java.math.BigDecimal.valueOf(50000);
        profileEntry.priorIncidentCount = 0;
        profileEntry.investigationPath  = "entity-resolution→sar-drafting";
        when(ledgerRepo.findBySubjectId(eq(caseId), any()))
                .thenReturn(List.of(
                        caseOpenedEntry(caseId, caseOpenedId),
                        reviewOpenedEntry(caseId, reviewOpenedId, taskId, caseOpenedId),
                        profileEntry));
        when(verificationService.inclusionProof(any(), any())).thenReturn(stubProof());
        when(attestationRepo.findByInvestigationCaseId(caseId)).thenReturn(List.of());
        when(workerDecisionRepo.findAllByCaseId(caseId)).thenReturn(List.of());
        when(mockReconciler.reconcileIfNeeded(any(), any(), any())).thenReturn(List.of());

        ComplianceEvidence evidence = service.assembleEvidence(caseId);

        assertEquals(RequirementStatus.GAP, evidence.art22DecisionRecord().status());
    }

    private io.casehub.aml.ledger.AmlCaseProfileLedgerEntry profileEntryWithSupplement(UUID caseId) {
        var entry = new io.casehub.aml.ledger.AmlCaseProfileLedgerEntry();
        entry.id                 = UUID.randomUUID();
        entry.subjectId          = caseId;
        entry.outcome            = "SAR_WARRANTED";
        entry.flagReason         = "HIGH_RISK_JURISDICTION";
        entry.transactionAmount  = java.math.BigDecimal.valueOf(50000);
        entry.priorIncidentCount = 0;
        entry.investigationPath  = "entity-resolution→sar-drafting";
        entry.attach(AmlComplianceSupplement.triageDecision(
                "SAR_WARRANTED", null, "entity-resolution→sar-drafting",
                "{\"flagReason\":\"HIGH_RISK_JURISDICTION\"}"));
        return entry;
    }


    private void setupMinimalLedgerEntries() {
        var opened = caseOpenedEntry(caseId, caseOpenedId);
        when(ledgerRepo.findBySubjectId(eq(caseId), any())).thenReturn(List.of(opened));
        when(workerDecisionRepo.findAllByCaseId(caseId)).thenReturn(List.of());
        when(attestationRepo.findByInvestigationCaseId(caseId)).thenReturn(List.of());
        when(mockReconciler.reconcileIfNeeded(eq(caseId), any(), any())).thenReturn(List.of());
    }
}
