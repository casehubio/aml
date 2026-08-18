package io.casehub.aml.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.aml.ComplianceReviewLifecycle;
import io.casehub.aml.cbr.CbrPathAdvisorWorker;
import io.casehub.aml.cbr.InvestigationTriageWorker;
import io.casehub.aml.domain.AmlActionType;
import io.casehub.aml.domain.EntityResolutionResult;
import io.casehub.aml.domain.FlagReason;
import io.casehub.aml.domain.InvestigationSummary;
import io.casehub.aml.domain.OsintResult;
import io.casehub.aml.domain.PatternAnalysisResult;
import io.casehub.aml.domain.SeedNarrative;
import io.casehub.aml.domain.SpecialistOutcome;
import io.casehub.aml.domain.SuspiciousTransaction;
import io.casehub.engine.flow.FlowWorkerFunction;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.preferences.PreferenceProvider;
import io.casehub.worker.api.PlannedAction;
import io.casehub.worker.api.Worker;
import io.casehub.worker.api.WorkerResult;

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
    private final ObjectMapper              objectMapper;
    private final LedgerEntryRepository     ledgerRepository;
    private final CurrentPrincipal          principal;
    private final PreferenceProvider        preferenceProvider;
    private final io.casehub.aml.cbr.SarNarrativeSeeder seeder;
    private final io.casehub.aml.investigation.SarNarrativeService sarNarrativeService;
    private final io.casehub.aml.RejectionEscalationLifecycle rejectionEscalationLifecycle;


    public AmlInvestigationCaseDescriptor(
            final ComplianceReviewLifecycle complianceReviewLifecycle,
            final ObjectMapper objectMapper,
            final LedgerEntryRepository ledgerRepository,
            final CurrentPrincipal principal,
            final PreferenceProvider preferenceProvider,
            final io.casehub.aml.cbr.SarNarrativeSeeder seeder,
            final io.casehub.aml.investigation.SarNarrativeService sarNarrativeService,
            final io.casehub.aml.RejectionEscalationLifecycle rejectionEscalationLifecycle) {
        this.complianceReviewLifecycle = complianceReviewLifecycle;
        this.objectMapper              = objectMapper;
        this.ledgerRepository          = ledgerRepository;
        this.principal                 = principal;
        this.preferenceProvider        = preferenceProvider;
        this.seeder                    = seeder;
        this.sarNarrativeService       = sarNarrativeService;
        this.rejectionEscalationLifecycle = rejectionEscalationLifecycle;
    }

    List<Worker> workers() {
        return List.of(
                entityResolutionWorker(),
                patternAnalysisWorker(),
                osintScreeningWorkerSenior(),
                seniorAnalystWorker(),
                InvestigationTriageWorker.create(objectMapper, preferenceProvider),
                CbrPathAdvisorWorker.create(ledgerRepository, principal, seeder, preferenceProvider),
                sarDraftingWorkerSenior(),
                complianceReviewOpeningWorker(),
                io.casehub.aml.cbr.RejectionReviewWorker.create(objectMapper),
                io.casehub.aml.cbr.PostRejectionTriageWorker.create(objectMapper, preferenceProvider),
                RejectionEscalationWorker.create(objectMapper, rejectionEscalationLifecycle),
                SarDraftingEscalatedWorker.create(objectMapper, sarNarrativeService),
                rejectionStallWorker()
                      );}

    private static Worker entityResolutionWorker() {
        return Worker.builder()
                     .name("entity-resolution-agent")
                     .capabilityName("entity-resolution")
                     .function(new FlowWorkerFunction(
                             workflow("entity-resolution")
                                     .tasks(
                                             function(s -> {
                                                 @SuppressWarnings("unchecked") final Map<String, Object> input = (Map<String, Object>) s;
                                                 @SuppressWarnings("unchecked") final Map<String, Object> tx = (Map<String, Object>) input.get("transaction");
                                                 final String flagReason = tx != null
                                                                           ? (String) tx.getOrDefault("flagReason", "") : "";
                                                 final boolean isPep = FlagReason.PEP_MATCH.name().equals(flagReason);
                                                 final boolean isHighRiskJurisdiction = FlagReason.HIGH_RISK_JURISDICTION.name().equals(flagReason);
                                                 final String txId = tx != null
                                                                     ? String.valueOf(tx.getOrDefault("id", "unknown")) : "unknown";
                                                 final String entityType = isPep ? "PEP" : isHighRiskJurisdiction ? "SHELL_COMPANY" : "CORPORATE";
                                                 final double riskScore = isPep ? 0.87 : isHighRiskJurisdiction ? 0.95 : 0.35;
                                                 return Map.of(
                                                         "entityId", "entity-" + txId,
                                                         "ownershipChain", isPep
                                                                           ? "Direct → PEP Principal"
                                                                           : isHighRiskJurisdiction
                                                                           ? "Shell Company → Offshore Entity"
                                                                           : "Direct → Corporate Entity",
                                                         "entityType", entityType,
                                                         "riskScore", riskScore
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
                     .fn((Map<String, Object>) null)
                     .apply((input, scope) -> {
                         final SuspiciousTransaction tx =
                                 objectMapper.convertValue(input.get("transaction"), SuspiciousTransaction.class);
                         final String sarNarrative = (String) input.get("sarNarrative");
                         final UUID   caseId       = scope.caseId();
                         final String complianceTaskId =
                                 complianceReviewLifecycle.openReview(
                                         tx, buildSummary(input, tx, sarNarrative), caseId);
                         return WorkerResult.of(Map.of("complianceTaskId", complianceTaskId));
                     })
                     .build();
    }

    private Worker sarDraftingWorkerSenior() {
        return Worker.builder()
                     .name("sar-drafting-agent-senior")
                     .capabilityName("sar-drafting")
                     .function((final Map<String, Object> input) -> {
                         final SuspiciousTransaction tx =
                                 objectMapper.convertValue(input.get("transaction"), SuspiciousTransaction.class);
                         final EntityResolutionResult entity =
                                 objectMapper.convertValue(input.get("entityResolution"), EntityResolutionResult.class);
                         final PatternAnalysisResult pattern =
                                 objectMapper.convertValue(input.get("patternAnalysis"), PatternAnalysisResult.class);
                         final OsintResult osint =
                                 objectMapper.convertValue(input.get("osintScreening"), OsintResult.class);
                         final List<SeedNarrative> seeds = deserializeSeeds(input.get("similarSarNarratives"));

                         final var context = new io.casehub.aml.investigation.NarrativeContext(tx, entity, pattern, osint, seeds);
                         final var result  = sarNarrativeService.draft(context);

                         final var output = new java.util.LinkedHashMap<String, Object>();
                         output.put("sarNarrative", result.narrative());
                         output.put("narrativeSeeded", result.seeded());
                         output.put("seedCount", result.seedCount());
                         output.put("adaptationMethod", result.adaptationMethod().name());

                         final String entityType = entity != null ? entity.entityType() : "UNKNOWN";
                         return WorkerResult.of(output,
                                                PlannedAction.of(
                                                        "SAR filing for transaction " + tx.id(),
                                                        AmlActionType.SAR_FILING.actionType(),
                                                        Map.of("transactionId", tx.id(),
                                                               "amount", String.valueOf(tx.amount()),
                                                               "currency", tx.currency(),
                                                               "entityType", entityType)));
                     })
                     .build();
    }

    @SuppressWarnings("unchecked")
    private List<SeedNarrative> deserializeSeeds(Object raw) {
        if (raw == null) {return List.of();}
        final var list = (List<Map<String, Object>>) raw;
        return list.stream()
                   .map(m -> {
                       try {
                           return objectMapper.convertValue(m, SeedNarrative.class);
                       } catch (IllegalArgumentException e) {
                           return null;
                       }
                   })
                   .filter(java.util.Objects::nonNull)
                   .toList();
    }


    private InvestigationSummary buildSummary(
            final Map<String, Object> input,
            final SuspiciousTransaction tx,
            final String sarNarrative) {
        final EntityResolutionResult entity =
                objectMapper.convertValue(input.get("entityResolution"), EntityResolutionResult.class);
        final OsintResult osint =
                objectMapper.convertValue(input.get("osintScreening"), OsintResult.class);
        final boolean osintDeclined = osint != null && osint.declined();
        final SpecialistOutcome<EntityResolutionResult> entityOutcome = entity != null
                                                                        ? new SpecialistOutcome.Completed<>(entity)
                                                                        : new SpecialistOutcome.Declined<>(
                "sar-agent", "entity-resolution", "missing from context");
        final SpecialistOutcome<PatternAnalysisResult> patternOutcome =
                new SpecialistOutcome.Completed<>(
                        new PatternAnalysisResult(false, "engine-driven investigation"));
        final SpecialistOutcome<OsintResult> osintOutcome = osintDeclined
                                                            ? new SpecialistOutcome.Declined<>(
                "osint-agent", "osint-screening",
                "insufficient clearance for PEP database access")
                                                            : (osint != null
                                                               ? new SpecialistOutcome.Completed<>(osint)
                                                               : new SpecialistOutcome.Completed<>(new OsintResult(false, false, false, "no matches")));
        return new InvestigationSummary(tx, entityOutcome, patternOutcome, osintOutcome, sarNarrative);
    }

    private static Worker rejectionStallWorker() {
        return Worker.builder()
                     .name("rejection-stall-agent")
                     .capabilityName("rejection-stall")
                     .function(new FlowWorkerFunction(
                             workflow("rejection-stall")
                                     .tasks(function(s -> Map.of("investigationStalled", true), Map.class))
                                     .build()))
                     .build();
    }


}
