package io.casehub.aml.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.aml.domain.AmlActionType;
import io.casehub.aml.domain.EntityResolutionResult;
import io.casehub.aml.domain.FlagReason;
import io.casehub.api.engine.YamlCaseHub;
import io.casehub.api.model.CaseDefinition;
import io.casehub.engine.flow.FlowWorkerFunction;
import io.casehub.worker.api.PlannedAction;
import io.casehub.worker.api.Worker;
import io.casehub.worker.api.WorkerResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;

import static io.serverlessworkflow.fluent.func.FuncWorkflowBuilder.workflow;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.function;

@ApplicationScoped
public class AmlOversightCaseHub extends YamlCaseHub {

    @Inject
    ObjectMapper objectMapper;

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

    private Worker entityResolutionWorker() {
        return Worker.builder()
                     .name("oversight-entity-resolution-agent")
                     .capabilityName("entity-resolution")
                     .function(new FlowWorkerFunction(
                             workflow("oversight-entity-resolution")
                                     .tasks(
                                             function(s -> {
                                                 @SuppressWarnings("unchecked") final Map<String, Object> input = (Map<String, Object>) s;
                                                 @SuppressWarnings("unchecked") final Map<String, Object> tx = (Map<String, Object>) input.get("transaction");
                                                 final String  flagReason = tx != null ? (String) tx.getOrDefault("flagReason", "") : "";
                                                 final boolean isPep      = FlagReason.PEP_MATCH.name().equals(flagReason);
                                                 final String  txId       = tx != null ? String.valueOf(tx.getOrDefault("id", "unknown")) : "unknown";
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

    private Worker entityLinkProposalWorker() {
        return Worker.builder()
                     .name("oversight-entity-link-proposal-agent")
                     .capabilityName("entity-link-proposal")
                     .function((final Map<String, Object> input) -> {
                         final EntityResolutionResult entity =
                                 objectMapper.convertValue(input.get("entityResolution"), EntityResolutionResult.class);
                         final String entityType = entity != null ? entity.entityType() : "UNKNOWN";
                         final double riskScore = entity != null ? entity.riskScore() : 0.0;
                         final String ownershipChain = entity != null ? entity.ownershipChain() : "";

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

    private Worker investigationSummaryWorker() {
        return Worker.builder()
                     .name("oversight-investigation-summary-agent")
                     .capabilityName("investigation-summary")
                     .function(new FlowWorkerFunction(
                             workflow("oversight-investigation-summary")
                                     .tasks(
                                             function(s -> {
                                                 @SuppressWarnings("unchecked") final Map<String, Object> input = (Map<String, Object>) s;
                                                 @SuppressWarnings("unchecked") final Map<String, Object> link = (Map<String, Object>) input.get("entityLinkProposal");
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
