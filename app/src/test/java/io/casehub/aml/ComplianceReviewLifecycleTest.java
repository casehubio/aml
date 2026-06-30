package io.casehub.aml;

import io.casehub.aml.domain.*;
import io.casehub.aml.ledger.AmlLedgerService;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.api.WorkItemCreateRequest;
import io.casehub.work.api.WorkItemPriority;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComplianceReviewLifecycleTest {

    @Mock AmlLedgerService mockLedger;

    private final SuspiciousTransaction tx = new SuspiciousTransaction(
            "TXN-CLR", "ACC-A", "ACC-B",
            new BigDecimal("75000"), "USD",
            Instant.parse("2024-06-01T00:00:00Z"), "Test");

    private final InvestigationSummary summary = new InvestigationSummary(
            tx,
            new SpecialistOutcome.Completed<>(new EntityResolutionResult("E-1", "chain", "CORPORATE", 0.35)),
            new SpecialistOutcome.Completed<>(new PatternAnalysisResult(true, "structuring")),
            new SpecialistOutcome.Declined<>("agent", "osint-screening", "clearance"),
            "narrative");

    private static WorkItem workItemWith(UUID id) {
        WorkItem wi = new WorkItem();
        wi.id = id;
        return wi;
    }

    @Test
    void openReview_creates30DayClaimDeadline_andWritesLedgerEntry() {
        AtomicReference<WorkItemCreateRequest> captured = new AtomicReference<>();
        UUID caseId = UUID.randomUUID();
        ComplianceReviewLifecycle lifecycle = new ComplianceReviewLifecycle(request -> {
            captured.set(request);
            return workItemWith(UUID.randomUUID());
        }, mockLedger);

        lifecycle.openReview(tx, summary, caseId);

        WorkItemCreateRequest req = captured.get();
        assertNotNull(req);
        assertEquals(WorkItemPriority.HIGH, req.priority);
        assertEquals("compliance-officers", req.candidateGroups);
        assertEquals("casehubio/aml/oversight", req.scope);

        // callerRef uses caseId UUID (not transaction.id()) with "aml:investigation:" prefix
        assertTrue(req.callerRef.startsWith("aml:investigation:"),
            "callerRef must start with aml:investigation:");
        assertDoesNotThrow(() -> UUID.fromString(req.callerRef.substring("aml:investigation:".length())),
            "callerRef suffix must be a valid UUID");

        long days = Duration.between(Instant.now(), req.claimDeadline).toDays();
        assertTrue(days >= 29 && days <= 30, "claimDeadline should be ~30 days, was " + days);

        // Verify ledger entry was written with the correct caseId
        verify(mockLedger).writeComplianceReviewOpened(eq(caseId), any());
    }

    @Test
    void openReview_returnsWorkItemId() {
        UUID expectedId = UUID.randomUUID();
        ComplianceReviewLifecycle lifecycle = new ComplianceReviewLifecycle(
                req -> workItemWith(expectedId), mockLedger);

        String taskId = lifecycle.openReview(tx, summary, UUID.randomUUID());
        assertEquals(expectedId.toString(), taskId);
    }
}
