package io.casehub.aml.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.aml.RejectionEscalationLifecycle;
import io.casehub.worker.api.Worker;
import io.casehub.worker.api.WorkerResult;

import java.util.Map;

public final class RejectionEscalationWorker {

    private RejectionEscalationWorker() {}

    public static Worker create(ObjectMapper objectMapper, RejectionEscalationLifecycle lifecycle) {
        return Worker.builder()
                     .name("rejection-escalation-agent")
                     .capabilityName("rejection-escalation")
                     .fn((Map<String, Object>) null)
                     .apply((input, scope) -> {
                         String payload;
                         try {
                             payload = objectMapper.writeValueAsString(input);
                         } catch (JsonProcessingException e) {
                             payload = "{}";
                         }
                         var taskId = lifecycle.openEscalation(scope.caseId(), payload);
                         return WorkerResult.of(Map.of(
                                 "escalationTaskId", taskId.toString(),
                                 "status", "PENDING"));
                     })
                     .build();
    }
}
