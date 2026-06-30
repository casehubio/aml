package io.casehub.aml.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.engine.flow.FlowWorkerFunction;
import io.casehub.api.model.WorkerExecutionContext;
import io.casehub.worker.api.Worker;
import io.casehub.worker.api.PlannedAction;
import io.casehub.worker.api.WorkerResult;
import io.casehub.aml.domain.AmlActionType;
import io.casehub.aml.ComplianceReviewLifecycle;
import io.casehub.aml.domain.EntityResolutionResult;
import io.casehub.aml.domain.InvestigationSummary;
import io.casehub.aml.domain.OsintResult;
import io.casehub.aml.domain.PatternAnalysisResult;
import io.casehub.aml.domain.SpecialistOutcome;
import io.casehub.aml.domain.SuspiciousTransaction;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.serverlessworkflow.fluent.func.FuncWorkflowBuilder.workflow;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.function;

/**
 * Descriptor carrying the business logic for the AML investigation case type.
 *
 * <p>A plain POJO — no CDI annotations. All worker functions and helper methods
 * for the AML investigation workflow live here. Constructed by
 * {@link AmlInvestigationCaseHub} with its CDI-managed dependencies.
 *
 * <p>6 of 8 workers use {@code FlowWorkerFunction} with
 * {@code FuncWorkflowBuilder.workflow().tasks(function(...)).build()} per protocol
 * PP-20260531-worker-func-exec. SAR-drafting workers ({@code sar-drafting-agent-junior},
 * {@code sar-drafting-agent-senior}) use {@code WorkerFunction.Sync} for PlannedAction
 * support (engine#564 — FlowWorkerExecutor does not yet support PlannedAction).
 *
 * <p>Testable without Quarkus: pass {@code null} for both constructor args for
 * structural tests (worker count, names, capabilities). Worker lambdas capture
 * their dependencies but do not invoke them during construction — {@code null}
 * is safe as long as the lambda bodies are not executed.
 */
public final class AmlInvestigationCaseDescriptor {

    private final ComplianceReviewLifecycle complianceReviewLifecycle;
    private final ObjectMapper objectMapper;

    public AmlInvestigationCaseDescriptor(
            final ComplianceReviewLifecycle complianceReviewLifecycle,
            final ObjectMapper objectMapper) {
        this.complianceReviewLifecycle = complianceReviewLifecycle;
        this.objectMapper = objectMapper;
    }

    List<Worker> workers() {
        return List.of(
                entityResolutionWorker(),
                patternAnalysisWorker(),
                osintScreeningWorker(),
                osintScreeningWorkerSenior(),
                seniorAnalystWorker(),
                sarDraftingWorkerJunior(),
                sarDraftingWorkerSenior(),
                complianceReviewOpeningWorker()
        );
    }


    private static Worker entityResolutionWorker() {
        return Worker.builder()
                .name("entity-resolution-agent")
                .capabilityName("entity-resolution")
                .function(new FlowWorkerFunction(
                    workflow("entity-resolution")
                        .tasks(
                            function(s -> {
                                @SuppressWarnings("unchecked")
                                final Map<String, Object> input = (Map<String, Object>) s;
                                @SuppressWarnings("unchecked")
                                final Map<String, Object> tx = (Map<String, Object>) input.get("transaction");
                                final String flagReason = tx != null
                                        ? (String) tx.getOrDefault("flagReason", "") : "";
                                final boolean isPep = flagReason != null && flagReason.contains("PEP");
                                final String txId = tx != null
                                        ? String.valueOf(tx.getOrDefault("id", "unknown")) : "unknown";
                                return Map.of(
                                        "entityId", "entity-" + txId,
                                        "ownershipChain", isPep
                                                ? "Direct → PEP Principal"
                                                : "Direct → Corporate Entity",
                                        "entityType", isPep ? "PEP" : "CORPORATE",
                                        "riskScore", isPep ? 0.87 : 0.35
                                );
                            }, Map.class))
                        .build()))
                .build();
    }

    private static Worker patternAnalysisWorker() {
        return Worker.builder()
                .name("pattern-analysis-agent")
                .capabilityName("pattern-analysis")
                .function(new FlowWorkerFunction(
                    workflow("pattern-analysis")
                        .tasks(
                            function(s -> Map.of(
                                    "structuringDetected", false,
                                    "description", "No structuring pattern detected in transaction cluster"
                            ), Map.class))
                        .build()))
                .build();
    }

    /**
     * OSINT screening always declines in Layer 5 stubs — demonstrates that DECLINE
     * is a first-class outcome: osintScreening.declined=true satisfies the sar-drafting
     * binding condition (osintScreening != null) so the investigation continues normally.
     */
    private static Worker osintScreeningWorker() {
        return Worker.builder()
                .name("osint-screening-agent")
                .capabilityName("osint-screening")
                .function(new FlowWorkerFunction(
                    workflow("osint-screening")
                        .tasks(
                            function(s -> Map.of(
                                    "declined", true,
                                    "reason", "insufficient clearance for PEP database access",
                                    "pepHit", false,
                                    "sanctionsHit", false
                            ), Map.class))
                        .build()))
                .build();
    }

    /**
     * Senior OSINT worker — full clearance, never declines. Demonstrates trust-based
     * routing: complex or PEP cases are routed to this worker rather than the junior.
     */
    private static Worker osintScreeningWorkerSenior() {
        return Worker.builder()
                .name("osint-screening-agent-senior")
                .capabilityName("osint-screening")
                .function(new FlowWorkerFunction(
                    workflow("osint-screening-senior")
                        .tasks(
                            function(s -> Map.of(
                                    "declined", false,
                                    "reason", "full-clearance",
                                    "pepHit", false,
                                    "sanctionsHit", false,
                                    "screeningLevel", "ENHANCED"
                            ), Map.class))
                        .build()))
                .build();
    }

    private static Worker seniorAnalystWorker() {
        return Worker.builder()
                .name("senior-analyst-agent")
                .capabilityName("senior-analyst-review")
                .function(new FlowWorkerFunction(
                    workflow("senior-analyst-review")
                        .tasks(
                            function(s -> Map.of(
                                    "reviewed", true,
                                    "recommendation",
                                    "PEP entity confirmed — enhanced due diligence required."
                                            + " Escalate to compliance director."
                            ), Map.class))
                        .build()))
                .build();
    }

    private Worker complianceReviewOpeningWorker() {
        return Worker.builder()
                .name("compliance-review-opening-agent")
                .capabilityName("compliance-review-opening")
                .function(new FlowWorkerFunction(
                    workflow("compliance-review-opening")
                        .tasks(
                            function(s -> {
                                @SuppressWarnings("unchecked")
                                final Map<String, Object> input = (Map<String, Object>) s;
                                @SuppressWarnings("unchecked")
                                final Map<String, Object> txMap =
                                        (Map<String, Object>) input.get("transaction");
                                final SuspiciousTransaction tx =
                                        objectMapper.convertValue(txMap, SuspiciousTransaction.class);
                                final String sarNarrative = (String) input.get("sarNarrative");
                                final UUID caseId = WorkerExecutionContext.current().caseId();
                                final String complianceTaskId =
                                        complianceReviewLifecycle.openReview(
                                                tx, buildSummary(input, tx, sarNarrative), caseId);
                                return Map.of("complianceTaskId", complianceTaskId);
                            }, Map.class))
                        .build()))
                .build();
    }

    private Worker sarDraftingWorkerJunior() {
        return Worker.builder()
                .name("sar-drafting-agent-junior")
                .capabilityName("sar-drafting")
                .function((final Map<String, Object> input) -> {
                    @SuppressWarnings("unchecked")
                    final Map<String, Object> txMap = (Map<String, Object>) input.get("transaction");
                    final SuspiciousTransaction tx =
                            objectMapper.convertValue(txMap, SuspiciousTransaction.class);
                    @SuppressWarnings("unchecked")
                    final Map<String, Object> entityMap =
                            (Map<String, Object>) input.get("entityResolution");
                    final String entityType = entityMap != null
                            ? (String) entityMap.getOrDefault("entityType", "UNKNOWN") : "UNKNOWN";
                    @SuppressWarnings("unchecked")
                    final Map<String, Object> osintMap =
                            (Map<String, Object>) input.get("osintScreening");
                    final boolean osintDeclined = osintMap != null
                            && Boolean.TRUE.equals(osintMap.get("declined"));
                    final String sarNarrative = "SAR filed for transaction " + tx.id()
                            + ". Amount: " + tx.amount() + " " + tx.currency()
                            + (osintDeclined ? " OSINT screening declined." : "");
                    return WorkerResult.of(
                            Map.of("sarNarrative", sarNarrative),
                            PlannedAction.of(
                                    "SAR filing for transaction " + tx.id(),
                                    AmlActionType.SAR_FILING.actionType(),
                                    Map.of("transactionId", tx.id(),
                                           "amount", tx.amount().toString(),
                                           "currency", tx.currency(),
                                           "entityType", entityType)));
                })
                .build();
    }

    private Worker sarDraftingWorkerSenior() {
        return Worker.builder()
                .name("sar-drafting-agent-senior")
                .capabilityName("sar-drafting")
                .function((final Map<String, Object> input) -> {
                    @SuppressWarnings("unchecked")
                    final Map<String, Object> txMap = (Map<String, Object>) input.get("transaction");
                    @SuppressWarnings("unchecked")
                    final Map<String, Object> entityMap =
                            (Map<String, Object>) input.get("entityResolution");
                    @SuppressWarnings("unchecked")
                    final Map<String, Object> osintMap =
                            (Map<String, Object>) input.get("osintScreening");
                    final SuspiciousTransaction tx =
                            objectMapper.convertValue(txMap, SuspiciousTransaction.class);
                    final String entityType = entityMap != null
                            ? (String) entityMap.getOrDefault("entityType", "UNKNOWN")
                            : "UNKNOWN";
                    final boolean osintDeclined = osintMap != null
                            && Boolean.TRUE.equals(osintMap.get("declined"));
                    final String sarNarrative = buildNarrative(tx, entityType, osintDeclined);
                    return WorkerResult.of(
                            Map.of("sarNarrative", sarNarrative),
                            PlannedAction.of(
                                    "SAR filing for transaction " + tx.id(),
                                    AmlActionType.SAR_FILING.actionType(),
                                    Map.of("transactionId", tx.id(),
                                           "amount", tx.amount().toString(),
                                           "currency", tx.currency(),
                                           "entityType", entityType)));
                })
                .build();
    }

    private InvestigationSummary buildSummary(
            final Map<String, Object> input,
            final SuspiciousTransaction tx,
            final String sarNarrative) {
        @SuppressWarnings("unchecked")
        final Map<String, Object> entityMap = (Map<String, Object>) input.get("entityResolution");
        @SuppressWarnings("unchecked")
        final Map<String, Object> osintMap = (Map<String, Object>) input.get("osintScreening");
        final boolean osintDeclined = osintMap != null && Boolean.TRUE.equals(osintMap.get("declined"));
        final SpecialistOutcome<EntityResolutionResult> entityOutcome = entityMap != null
                ? new SpecialistOutcome.Completed<>(
                        objectMapper.convertValue(entityMap, EntityResolutionResult.class))
                : new SpecialistOutcome.Declined<>(
                        "sar-agent", "entity-resolution", "missing from context");
        final SpecialistOutcome<PatternAnalysisResult> patternOutcome =
                new SpecialistOutcome.Completed<>(
                        new PatternAnalysisResult(false, "engine-driven investigation"));
        final SpecialistOutcome<OsintResult> osintOutcome = osintDeclined
                ? new SpecialistOutcome.Declined<>(
                        "osint-agent", "osint-screening",
                        "insufficient clearance for PEP database access")
                : new SpecialistOutcome.Completed<>(new OsintResult(false, false, "no matches"));
        return new InvestigationSummary(tx, entityOutcome, patternOutcome, osintOutcome, sarNarrative);
    }

    private static String buildNarrative(
            final SuspiciousTransaction tx,
            final String entityType,
            final boolean osintDeclined) {
        final String osintNote = osintDeclined
                ? " OSINT screening declined (insufficient clearance for PEP database access)."
                : "";
        return "SAR narrative for transaction " + tx.id()
                + ". Entity type: " + entityType
                + ". Amount: " + tx.amount() + " " + tx.currency()
                + ". Flag reason: " + tx.flagReason()
                + "." + osintNote;
    }
}
