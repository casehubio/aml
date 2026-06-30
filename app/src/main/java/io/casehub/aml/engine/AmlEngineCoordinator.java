package io.casehub.aml.engine;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.aml.domain.SuspiciousTransaction;
import io.casehub.aml.ledger.AmlLedgerService;
import io.casehub.aml.memory.AmlMemoryService;
import io.casehub.aml.memory.AmlPriorContext;
import io.casehub.aml.query.InvestigationSummaryService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Layer 5 coordinator — starts a casehub-engine case for an AML investigation.
 *
 * <p>Returns the case UUID immediately; the engine drives the investigation asynchronously
 * via binding evaluation and Quartz worker execution. The caller can poll the event log
 * or the ledger to observe investigation progress.
 *
 * <p>Layer 8: prior context from {@link AmlMemoryService} is injected into the initial case
 * context as {@code priorEntityContext} so engine bindings and specialist agents can
 * fast-path decisions based on known-high-risk entities or prior investigation history.
 */
@ApplicationScoped
public class AmlEngineCoordinator {

    private static final Logger LOG = Logger.getLogger(AmlEngineCoordinator.class);
    private static final int CASE_START_TIMEOUT_SECONDS = 5;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    @Inject AmlInvestigationCaseHub caseHub;
    @Inject AmlLedgerService ledgerService;
    @Inject ObjectMapper objectMapper;
    @Inject AmlMemoryService memoryService;
    @Inject InvestigationSummaryService summaryService;

    /**
     * Starts an AML investigation case and returns its UUID.
     *
     * <p>The case UUID is the stable identifier for the investigation — it links the
     * engine event log, the AML ledger entries, and the compliance officer WorkItem.
     *
     * <p>Prior entity context is fetched from {@link AmlMemoryService} and included in
     * the initial context map. If the query fails, a cold (empty) context is used and a
     * WARN is logged — the investigation proceeds regardless.
     */
    public UUID startInvestigation(final SuspiciousTransaction transaction) {
        final Map<String, Object> txMap = objectMapper.convertValue(transaction, MAP_TYPE);
        final AmlPriorContext priorContext = queryPriorContext(transaction);

        final Map<String, Object> initialContext = new HashMap<>();
        initialContext.put("transaction", txMap);
        initialContext.put("priorEntityContext", priorContext.toContextMap());

        final UUID caseId;
        try {
            // startCase() returns CompletionStage<UUID> — the UUID is the engine's internal
            // case instance ID. We block briefly to get it, then return; the investigation
            // proceeds asynchronously via Quartz worker execution.
            caseId = caseHub.startCase(initialContext)
                    .toCompletableFuture()
                    .get(CASE_START_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to start AML investigation case for transaction %s",
                    transaction.id());
            throw new RuntimeException("Failed to start investigation case", e);
        }

        // Create investigation summary row before engine workers fire — the observer needs
        // this row to exist when CaseLifecycleEvent fires.
        summaryService.createSummary(caseId, transaction);

        // Layer 4 continuity: record investigation start using the engine's case UUID so
        // ledger entries and engine event log share the same stable identifier.
        ledgerService.writeCaseOpened(transaction, caseId);
        LOG.infof("AML investigation started: caseId=%s txId=%s hasHistory=%b knownHighRisk=%b",
                caseId, transaction.id(), priorContext.hasHistory(), priorContext.isKnownHighRisk());
        return caseId;
    }

    private AmlPriorContext queryPriorContext(final SuspiciousTransaction transaction) {
        try {
            return memoryService.queryPriorContext(transaction);
        } catch (Exception e) {
            LOG.warnf(e, "Prior context query failed for transaction %s — starting cold",
                    transaction.id());
            return AmlPriorContext.empty();
        }
    }
}
