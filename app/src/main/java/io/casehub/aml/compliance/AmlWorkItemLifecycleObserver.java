package io.casehub.aml.compliance;

import io.casehub.aml.ledger.AmlLedgerService;
import io.casehub.work.api.WorkItemStatus;
import io.casehub.work.runtime.event.WorkItemLifecycleEvent;
import io.casehub.work.runtime.model.WorkItemEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.UUID;

@ApplicationScoped
public class AmlWorkItemLifecycleObserver {

    private static final Logger LOG               = Logger.getLogger(AmlWorkItemLifecycleObserver.class);
    private static final String CALLER_REF_PREFIX = "aml:investigation:";

    private final AmlLedgerService            ledgerService;
    private final ComplianceEscalationService escalationService;

    @Inject
    public AmlWorkItemLifecycleObserver(AmlLedgerService ledgerService,
                                        ComplianceEscalationService escalationService) {
        this.ledgerService     = ledgerService;
        this.escalationService = escalationService;
    }

    public void onWorkItemLifecycle(@ObservesAsync WorkItemLifecycleEvent event) {
        final WorkItemEntity workItem = event.workItem();
        if (workItem == null) {
            return;
        }

        final String callerRef = workItem.callerRef;
        if (callerRef == null || !callerRef.startsWith(CALLER_REF_PREFIX)) {
            return;
        }

        final UUID caseId;
        try {
            caseId = UUID.fromString(callerRef.substring(CALLER_REF_PREFIX.length()));
        } catch (IllegalArgumentException e) {
            LOG.warnf("Invalid caseId in callerRef '%s' — skipping", callerRef);
            return;
        }

        if (event.status() == WorkItemStatus.EXPIRED) {
            escalationService.escalateToSeniorCompliance(caseId, workItem);
            return;
        }

        if (event.status() != WorkItemStatus.COMPLETED
            && event.status() != WorkItemStatus.REJECTED) {
            return;
        }

        final String officerId = event.actor() != null ? event.actor() : "unknown-officer";
        final String reviewDecision = event.status() == WorkItemStatus.COMPLETED
                                      ? "APPROVED" : "REJECTED";
        final String rejectionReason = event.status() == WorkItemStatus.REJECTED
                                       ? event.detail() : null;

        boolean written = false;
        try {
            ledgerService.writeSarOfficerReviewed(caseId, officerId, reviewDecision, rejectionReason);
            written = true;
        } catch (Exception e) {
            LOG.warnf(e, "Failed to write SAR_OFFICER_REVIEWED for caseId=%s officer=%s",
                      caseId, officerId);
            if (!written) {
                try {
                    ledgerService.writeSarOfficerReviewedFailure(caseId, officerId, reviewDecision, rejectionReason);
                } catch (Exception inner) {
                    LOG.errorf(inner,
                               "AUDIT GAP: SAR_OFFICER_REVIEWED failure entry also failed caseId=%s",
                               caseId);
                }
            }
        }
    }
}
