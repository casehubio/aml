package io.casehub.aml;

import io.casehub.aml.domain.InvestigationSummary;
import io.casehub.aml.domain.SpecialistOutcome;
import io.casehub.aml.domain.SuspiciousTransaction;
import io.casehub.aml.ledger.AmlLedgerService;
import io.casehub.work.api.WorkItemCreateRequest;
import io.casehub.work.api.WorkItemPriority;
import io.casehub.work.api.WorkItem;
import io.casehub.work.runtime.service.WorkItemService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.function.Function;

/**
 * Opens a compliance officer review WorkItem and writes the COMPLIANCE_REVIEW_OPENED
 * ledger entry in a single consolidated call.
 *
 * <p>Previously, AmlInvestigationCoordinator called openReview() and then separately
 * called ledgerService.writeComplianceReviewOpened(). The engine path (Quartz workers)
 * called only openReview(), never writing the ledger entry (aml#56). This consolidation
 * ensures both operations always occur together regardless of the caller path.
 *
 * <p>Not @Transactional: WorkItemService.create() writes to the default datasource;
 * writeComplianceReviewOpened() writes to the qhorus datasource. These are two separate
 * non-XA datasources — Narayana LRC allows only one non-XA resource per XA transaction.
 * Partial-failure risk (WorkItem committed, ledger write fails) is accepted.
 */
@ApplicationScoped
public class ComplianceReviewLifecycle {
    private static final java.util.List<io.casehub.work.api.Outcome> REVIEW_OUTCOMES = java.util.List.of(
            new io.casehub.work.api.Outcome("file", "File SAR with FinCEN", null),
            new io.casehub.work.api.Outcome("clear", "Clear — no suspicious activity", null),
            new io.casehub.work.api.Outcome("escalate", "Escalate to senior compliance", null)
                                                                                                        );


    private final Function<WorkItemCreateRequest, WorkItem> creator;
    private final AmlLedgerService                                ledgerService;

    @Inject
    public ComplianceReviewLifecycle(WorkItemService workItemService,
                                     AmlLedgerService ledgerService) {
        this.creator = workItemService::create;
        this.ledgerService = ledgerService;
    }

    // Package-private test constructor
    ComplianceReviewLifecycle(Function<WorkItemCreateRequest, WorkItem> creator,
                              AmlLedgerService ledgerService) {
        this.creator = creator;
        this.ledgerService = ledgerService;
    }

    @ActivateRequestContext
    public String openReview(SuspiciousTransaction transaction, InvestigationSummary summary,
                             UUID caseId) {
        String osintNote = summary.osintScreening() instanceof SpecialistOutcome.Declined<?> d
                           ? " OSINT declined: " + d.reason() + "." : "";
        WorkItemEntity workItem = creator.apply(WorkItemCreateRequest.builder()
                                                                     .title("Compliance review — SAR for transaction " + transaction.id())
                                                                     .description(summary.sarNarrative() + osintNote)
                                                                     .priority(WorkItemPriority.HIGH)
                                                                     .candidateGroups("compliance-officers")
                                                                     .createdBy("aml-system")
                                                                     .claimDeadline(Instant.now().plus(30, ChronoUnit.DAYS))
                                                                     .callerRef("aml:investigation:" + caseId)
                                                                     .scope("casehubio/aml/oversight")
                                                                     .permittedOutcomes(REVIEW_OUTCOMES)
                                                                     .formKey("aml-sar-compliance-review")
                                                                     .build());
        final String taskId = workItem.id.toString();
        ledgerService.writeComplianceReviewOpened(caseId, taskId);
        return taskId;
    }
}
