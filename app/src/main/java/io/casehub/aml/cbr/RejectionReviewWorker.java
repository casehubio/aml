package io.casehub.aml.cbr;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.engine.flow.FlowWorkerFunction;
import io.casehub.worker.api.Worker;

import java.util.Map;

import static io.serverlessworkflow.fluent.func.FuncWorkflowBuilder.workflow;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.function;

public final class RejectionReviewWorker {

    private RejectionReviewWorker() {}

    @SuppressWarnings("unchecked")
    public static Worker create(ObjectMapper objectMapper) {
        return Worker.builder()
                     .name("rejection-review-agent")
                     .capabilityName("rejection-review")
                     .function(new FlowWorkerFunction(
                             workflow("rejection-review")
                                     .tasks(function(s -> {
                                         Map<String, Object> input = (Map<String, Object>) s;
                                         Map<String, Object> rejection =
                                                 (Map<String, Object>) input.get("rejectionContext");
                                         String actionType = rejection != null
                                                 ? (String) rejection.getOrDefault("actionType", "") : "";
                                         boolean isSarRejection = "sar.filing".equals(actionType);
                                         return Map.of(
                                                 "riskAdjustment", isSarRejection ? -0.15 : 0.1,
                                                 "finding", isSarRejection
                                                         ? "Entity structure reassessed — legitimate corporate activity"
                                                         : "Additional OSINT screening confirms initial risk indicators",
                                                 "recommendedAction", isSarRejection ? "LOWER_RISK" : "MAINTAIN_RISK");
                                     }, Map.class))
                                     .build()))
                     .build();
    }
}
