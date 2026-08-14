package io.casehub.aml.compliance;

import io.casehub.aml.ledger.AmlLedgerService;
import io.casehub.work.api.WorkItemStatus;
import io.casehub.work.runtime.event.WorkItemLifecycleEvent;
import io.casehub.work.api.WorkItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AmlWorkItemLifecycleObserverTest {

    @Mock
    AmlLedgerService            ledgerService;
    @Mock
    ComplianceEscalationService escalationService;
    AmlWorkItemLifecycleObserver observer;

    UUID caseId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        observer = new AmlWorkItemLifecycleObserver(ledgerService, escalationService);
    }

    @Test
    void completed_validCallerRef_writesApproved() {
        observer.onWorkItemLifecycle(event(WorkItemStatus.COMPLETED, "aml:investigation:" + caseId,
                "compliance-officer-001"));

        verify(ledgerService).writeSarOfficerReviewed(eq(caseId), eq("compliance-officer-001"),
                eq("APPROVED"), eq(null));
        verify(ledgerService, never()).writeSarOfficerReviewedFailure(any(), any(), any(), any());
    }

    @Test
    void rejected_validCallerRef_writesRejected() {
        observer.onWorkItemLifecycle(event(WorkItemStatus.REJECTED, "aml:investigation:" + caseId,
                "compliance-officer-001"));

        verify(ledgerService).writeSarOfficerReviewed(eq(caseId), eq("compliance-officer-001"),
                eq("REJECTED"), eq(null));
    }

    @Test
    void inProgress_noWrite() {
        observer.onWorkItemLifecycle(event(WorkItemStatus.IN_PROGRESS, "aml:investigation:" + caseId,
                "officer"));

        verify(ledgerService, never()).writeSarOfficerReviewed(any(), any(), any(), any());
        verify(ledgerService, never()).writeSarOfficerReviewedFailure(any(), any(), any(), any());
    }

    @Test
    void nullWorkItem_noWrite() {
        // Use fromWire() to construct an event without a WorkItem snapshot
        // (WorkItemLifecycleEvent.of() NPEs on null workItem)
        WorkItemLifecycleEvent event = WorkItemLifecycleEvent.fromWire(
                "io.casehub.work.workitem.completed",
                "/workitems/" + UUID.randomUUID(),
                UUID.randomUUID().toString(),
                UUID.randomUUID(), WorkItemStatus.COMPLETED,
                Instant.now(), "officer", null, null, null, null, null,
                null, null, null, null, null);
        observer.onWorkItemLifecycle(event);

        verify(ledgerService, never()).writeSarOfficerReviewed(any(), any(), any(), any());
    }

    @Test
    void oldFormatCallerRef_noWrite() {
        observer.onWorkItemLifecycle(event(WorkItemStatus.COMPLETED,
                "aml:investigation/TXN-001", "officer"));

        verify(ledgerService, never()).writeSarOfficerReviewed(any(), any(), any(), any());
    }

    @Test
    void differentDomain_noWrite() {
        observer.onWorkItemLifecycle(event(WorkItemStatus.COMPLETED,
                "devtown:pr-review:" + UUID.randomUUID(), "actor"));

        verify(ledgerService, never()).writeSarOfficerReviewed(any(), any(), any(), any());
    }

    @Test
    void invalidUuidInCallerRef_noWrite() {
        observer.onWorkItemLifecycle(event(WorkItemStatus.COMPLETED,
                "aml:investigation:not-a-uuid", "officer"));

        verify(ledgerService, never()).writeSarOfficerReviewed(any(), any(), any(), any());
    }

    @Test
    void nullActor_fallsBackToUnknown() {
        observer.onWorkItemLifecycle(event(WorkItemStatus.COMPLETED,
                "aml:investigation:" + caseId, null));

        verify(ledgerService).writeSarOfficerReviewed(eq(caseId), eq("unknown-officer"),
                eq("APPROVED"), eq(null));
    }

    @Test
    void ledgerWriteFails_writesFailureEntry() {
        doThrow(new RuntimeException("DB error"))
            .when(ledgerService).writeSarOfficerReviewed(any(), any(), any(), any());

        observer.onWorkItemLifecycle(event(WorkItemStatus.COMPLETED,
                "aml:investigation:" + caseId, "officer-X"));

        verify(ledgerService).writeSarOfficerReviewedFailure(eq(caseId), eq("officer-X"), eq("APPROVED"), eq(null));
    }

    @Test
    void rejected_captures_rejection_reason_from_event_detail() {
        observer.onWorkItemLifecycle(event(WorkItemStatus.REJECTED,
                "aml:investigation:" + caseId, "compliance-officer-001",
                "Insufficient evidence for SAR filing"));

        verify(ledgerService).writeSarOfficerReviewed(eq(caseId),
                eq("compliance-officer-001"), eq("REJECTED"),
                eq("Insufficient evidence for SAR filing"));
    }

    @Test
    void expired_escalatesToSeniorCompliance() {
        WorkItem wi = WorkItem.builder()
                .id(UUID.randomUUID())
                .status(WorkItemStatus.EXPIRED)
                .callerRef("aml:investigation:" + caseId)
                .title("Compliance review — SAR for transaction TXN-001")
                .description("SAR narrative")
                .build();
        WorkItemLifecycleEvent expiredEvent = WorkItemLifecycleEvent.of(
                WorkItemStatus.EXPIRED.name(), wi, "system", null);

        observer.onWorkItemLifecycle(expiredEvent);

        verify(escalationService).escalateToSeniorCompliance(eq(caseId), eq(wi));
        verify(ledgerService, never()).writeSarOfficerReviewed(any(), any(), any(), any());
    }


    // -- Helpers --

    private WorkItemLifecycleEvent event(WorkItemStatus status, String callerRef, String actor) {
        return event(status, callerRef, actor, null);
    }

    private WorkItemLifecycleEvent event(WorkItemStatus status, String callerRef,
            String actor, String detail) {
        WorkItem wi = WorkItem.builder()
                .id(UUID.randomUUID())
                .status(status)
                .callerRef(callerRef)
                .build();
        return WorkItemLifecycleEvent.of(status.name(), wi, actor, detail);
    }
}
