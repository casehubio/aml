package io.casehub.aml.query;

import io.casehub.aml.domain.SuspiciousTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.UUID;

/**
 * Service layer for creating and updating InvestigationSummaryView.
 * <p>
 * All methods use REQUIRES_NEW to ensure they commit independently — the observer
 * calls these from a CDI async thread, not within the engine's transaction.
 */
@ApplicationScoped
public class InvestigationSummaryService {

    private static final Logger LOG = Logger.getLogger(InvestigationSummaryService.class);

    @Inject InvestigationSummaryRepository repository;

    /**
     * Create a new investigation summary row for the given case.
     * <p>
     * Called by {@link io.casehub.aml.engine.AmlEngineCoordinator} on investigation start,
     * after caseId generation but before engine start. This ensures the summary row exists
     * for the observer to update.
     *
     * @param caseId the engine case instance ID
     * @param txn the flagged transaction
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void createSummary(UUID caseId, SuspiciousTransaction txn) {
        var summary = new InvestigationSummaryView(
            caseId, txn.id(), txn.originAccountId(), txn.destinationAccountId(),
            txn.amount(), txn.currency(), txn.flagReason());
        repository.persist(summary);
        LOG.debugf("Investigation summary created: caseId=%s txId=%s", caseId, txn.id());
    }

    /**
     * Update the investigation status.
     * <p>
     * Called by {@link InvestigationSummaryObserver} on every CaseLifecycleEvent
     * with non-null caseStatus.
     *
     * @param caseId the engine case instance ID
     * @param status the new status (e.g. "COMPLETED", "FAILED", "CANCELLED")
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void updateStatus(UUID caseId, String status) {
        repository.findByCaseId(caseId)
            .ifPresent(summary -> {
                summary.updateStatus(status);
                LOG.debugf("Investigation summary status updated: caseId=%s status=%s",
                    caseId, status);
            });
    }

    /**
     * Update the investigation outcome type.
     * <p>
     * Called by {@link InvestigationSummaryObserver} when caseStatus transitions to COMPLETED.
     *
     * @param caseId the engine case instance ID
     * @param outcomeType the outcome type (e.g. "SAR_FILED", "SAR_DECLINED")
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void updateOutcome(UUID caseId, String outcomeType) {
        repository.findByCaseId(caseId)
            .ifPresent(summary -> {
                summary.updateOutcomeType(outcomeType);
                LOG.debugf("Investigation summary outcome updated: caseId=%s outcome=%s",
                    caseId, outcomeType);
            });
    }
}
