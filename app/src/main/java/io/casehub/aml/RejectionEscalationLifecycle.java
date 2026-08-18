package io.casehub.aml;

import io.casehub.aml.domain.AmlGroups;
import io.casehub.api.engine.CaseHubRuntime;
import io.casehub.work.api.Outcome;
import io.casehub.work.api.WorkItem;
import io.casehub.work.api.WorkItemCreateRequest;
import io.casehub.work.api.WorkItemPriority;
import io.casehub.work.runtime.event.WorkItemLifecycleEvent;
import io.casehub.work.runtime.service.WorkItemService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class RejectionEscalationLifecycle {

    private static final Logger LOG = Logger.getLogger(RejectionEscalationLifecycle.class);
    private static final String CALLER_REF_PREFIX = "aml:escalation:";
    private static final List<Outcome> ESCALATION_OUTCOMES = List.of(
            new Outcome("FILE_SAR", "Override MLRO — file SAR with FinCEN", null),
            new Outcome("NO_SAR", "Agree with MLRO — no SAR required", null),
            new Outcome("CLEAR", "Clear investigation — no further action", null));

    @Inject
    WorkItemService workItemService;

    @Inject
    CaseHubRuntime runtime;

    @ActivateRequestContext
    public UUID openEscalation(UUID caseId, String evidencePayload) {
        WorkItem workItem = workItemService.create(WorkItemCreateRequest.builder()
                .title("Gate rejection escalation — head of compliance review")
                .description(evidencePayload)
                .priority(WorkItemPriority.URGENT)
                .candidateGroups(AmlGroups.AML_SENIOR_COMPLIANCE)
                .createdBy("casehub-engine")
                .claimDeadline(Instant.now().plus(30, ChronoUnit.DAYS))
                .callerRef(CALLER_REF_PREFIX + caseId)
                .scope("casehubio/aml/escalation")
                .permittedOutcomes(ESCALATION_OUTCOMES)
                .formKey("aml-rejection-escalation-review")
                .build());
        LOG.infof("Rejection escalation WorkItem created: caseId=%s workItemId=%s", caseId, workItem.id());
        return workItem.id();
    }

    @ActivateRequestContext
    void onWorkItemCompleted(@ObservesAsync WorkItemLifecycleEvent event) {
        String callerRef = event.callerRef();
        if (callerRef == null || !callerRef.startsWith(CALLER_REF_PREFIX)) {return;}
        if (!event.status().isTerminal()) {return;}

        UUID   caseId     = UUID.fromString(callerRef.substring(CALLER_REF_PREFIX.length()));
        String resolution = event.resolution();
        String decision   = mapResolution(resolution);
        LOG.infof("Rejection escalation resolved: caseId=%s decision=%s resolution=%s",
                  caseId, decision, resolution);
        runtime.signal(caseId, Map.of(
                "rejectionEscalation", Map.of(
                        "decision", decision,
                        "reason", resolution != null ? resolution : "")));
    }

    private static String mapResolution(String resolution) {
        if (resolution == null) return "NO_SAR";
        return switch (resolution.toUpperCase()) {
            case "FILE_SAR" -> "FILE_SAR";
            case "CLEAR" -> "CLEAR";
            default -> "NO_SAR";
        };
    }
}
