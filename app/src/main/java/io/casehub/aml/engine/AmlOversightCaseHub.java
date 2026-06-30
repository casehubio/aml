package io.casehub.aml.engine;

import io.casehub.api.engine.YamlCaseHub;
import io.casehub.api.model.CaseDefinition;
import io.casehub.engine.flow.FlowWorkerFunction;
import io.casehub.worker.api.PlannedAction;
import io.casehub.worker.api.Worker;
import io.casehub.worker.api.WorkerResult;
import io.casehub.aml.domain.AmlActionType;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Map;

import static io.serverlessworkflow.fluent.func.FuncWorkflowBuilder.workflow;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.function;

/**
 * Layer 9 case hub — oversight gate demonstration.
 *
 * <p>Loads {@code aml/aml-oversight-investigation.yaml} and augments it with three in-process
 * workers. The entity-link-proposal worker declares a {@link PlannedAction} with action type
 * {@code ENTITY_LINK_CREATION} so the engine invokes {@code AmlActionRiskClassifier} before
 * committing the worker's output to the case context.
 *
 * <p>PEP entities and high-risk scores (riskScore ≥ 0.8) trigger a
 * {@link io.casehub.api.spi.RiskDecision.GateRequired}; low-risk CORPORATE cases return
 * {@link io.casehub.api.spi.RiskDecision.Autonomous} and proceed without a gate.
 */
@ApplicationScoped
public class AmlOversightCaseHub extends YamlCaseHub {

    public AmlOversightCaseHub() {
        super("aml/aml-oversight-investigation.yaml");
    }

    @Override
    protected void augment(CaseDefinition definition) {
        definition.getWorkers().addAll(List.of(
            entityResolutionWorker(),
            entityLinkProposalWorker(),
            investigationSummaryWorker()
        ));
    }

    private static Worker entityResolutionWorker() {
        return Worker.builder()
            .name("oversight-entity-resolution-agent")
            .capabilityName("entity-resolution")
            .function(new FlowWorkerFunction(
                workflow("oversight-entity-resolution")
                    .tasks(
                        function(s -> {
                            @SuppressWarnings("unchecked")
                            final Map<String, Object> input = (Map<String, Object>) s;
                            @SuppressWarnings("unchecked")
                            final Map<String, Object> tx = (Map<String, Object>) input.get("transaction");
                            final String flagReason = tx != null ? (String) tx.getOrDefault("flagReason", "") : "";
                            final boolean isPep = flagReason != null && flagReason.contains("PEP");
                            final String txId = tx != null ? String.valueOf(tx.getOrDefault("id", "unknown")) : "unknown";
                            return Map.of(
                                "entityId", "entity-" + txId,
                                "ownershipChain", isPep ? "Direct → PEP Principal" : "Direct → Corporate Entity",
                                "entityType", isPep ? "PEP" : "CORPORATE",
                                "riskScore", isPep ? 0.87 : 0.35
                            );
                        }, Map.class))
                    .build()))
            .build();
    }

    private static Worker entityLinkProposalWorker() {
        return Worker.builder()
            .name("oversight-entity-link-proposal-agent")
            .capabilityName("entity-link-proposal")
            .function((final Map<String, Object> input) -> {
                @SuppressWarnings("unchecked")
                final Map<String, Object> entityResolution = (Map<String, Object>) input.get("entityResolution");
                final String entityType = entityResolution != null
                    ? (String) entityResolution.getOrDefault("entityType", "UNKNOWN") : "UNKNOWN";
                final double riskScore = entityResolution != null
                    ? ((Number) entityResolution.getOrDefault("riskScore", 0.0)).doubleValue() : 0.0;
                final String ownershipChain = entityResolution != null
                    ? (String) entityResolution.getOrDefault("ownershipChain", "") : "";

                return WorkerResult.of(
                    Map.of("proposedLink", entityType + " → investigation graph",
                           "entityType", entityType, "riskScore", riskScore),
                    PlannedAction.of(
                        "Entity network link proposed: " + entityType,
                        AmlActionType.ENTITY_LINK_CREATION.actionType(),
                        Map.of("entityType", entityType, "riskScore", riskScore, "ownershipChain", ownershipChain)));
            })
            .build();
    }

    private static Worker investigationSummaryWorker() {
        return Worker.builder()
            .name("oversight-investigation-summary-agent")
            .capabilityName("investigation-summary")
            .function(new FlowWorkerFunction(
                workflow("oversight-investigation-summary")
                    .tasks(
                        function(s -> {
                            @SuppressWarnings("unchecked")
                            final Map<String, Object> input = (Map<String, Object>) s;
                            @SuppressWarnings("unchecked")
                            final Map<String, Object> link = (Map<String, Object>) input.get("entityLinkProposal");
                            final String entityType = link != null
                                ? (String) link.getOrDefault("entityType", "UNKNOWN") : "UNKNOWN";
                            return Map.of(
                                "summary", "Entity link confirmed for " + entityType + " entity",
                                "status", "LINK_CONFIRMED"
                            );
                        }, Map.class))
                    .build()))
            .build();
    }
}
