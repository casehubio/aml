package io.casehub.aml.query;

import io.casehub.aml.domain.InvestigationOutcome;
import io.casehub.aml.engine.AmlInvestigationOutcomeService;
import io.casehub.engine.common.spi.event.CaseLifecycleEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * CDI observer that updates InvestigationSummaryView on CaseLifecycleEvent.
 * <p>
 * Uses @ObservesAsync to match how the engine fires CaseLifecycleEvent. All writes
 * delegate to {@link InvestigationSummaryService}, which uses REQUIRES_NEW transactions
 * so updates commit independently of the engine's transaction.
 * <p>
 * Double-try/catch resilience pattern: outer try for status update (always attempted),
 * inner try for outcome resolution (COMPLETED only). If outcome resolution fails, the
 * status update still commits. If either fails, a WARN is logged but the observer does
 * not rethrow — CDI async observers should not fail the event dispatch.
 */
@ApplicationScoped
public class InvestigationSummaryObserver {

    private static final Logger LOG = Logger.getLogger(InvestigationSummaryObserver.class);

    @Inject InvestigationSummaryService summaryService;
    @Inject AmlInvestigationOutcomeService outcomeService;

    /**
     * Observe CaseLifecycleEvent and update the investigation summary.
     * <p>
     * Fires on every case lifecycle transition. Filters by caseStatus — if null, this is
     * not a status-change event and is ignored.
     * <p>
     * For COMPLETED events, resolves the outcome from the ledger and updates outcomeType.
     * For FAILED, CANCELLED, SUSPENDED, updates status only.
     *
     * @param event the case lifecycle event
     */
    void onCaseLifecycle(@ObservesAsync CaseLifecycleEvent event) {
        // Filter: only status-change events have non-null caseStatus
        if (event.caseStatus() == null) {
            return;
        }

        try {
            // Always update status — even if outcome resolution fails later
            summaryService.updateStatus(event.caseId(), event.caseStatus());

            // Outcome resolution only for COMPLETED cases
            if ("COMPLETED".equals(event.caseStatus())) {
                try {
                    InvestigationOutcome outcome = outcomeService.resolveOutcome(event.caseId());
                    if (outcome != null) {
                        summaryService.updateOutcome(event.caseId(), outcome.type());
                    } else {
                        LOG.warnf("No outcome resolved for completed case %s", event.caseId());
                    }
                } catch (Exception e) {
                    LOG.warnf(e, "Failed to resolve outcome for case %s — status updated, outcome null",
                        event.caseId());
                }
            }
        } catch (Exception e) {
            LOG.errorf(e, "Failed to update investigation summary for case %s", event.caseId());
        }
    }
}
