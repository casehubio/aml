package io.casehub.aml.compliance;

import io.casehub.work.api.WorkItemCreateRequest;
import io.casehub.work.api.WorkItemPriority;
import io.casehub.work.runtime.model.WorkItemEntity;
import io.casehub.work.runtime.service.WorkItemService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@ApplicationScoped
public class ComplianceEscalationService {

    private static final Logger LOG = Logger.getLogger(ComplianceEscalationService.class);

    private final WorkItemService workItemService;

    @Inject
    public ComplianceEscalationService(WorkItemService workItemService) {
        this.workItemService = workItemService;
    }

    public void escalateToSeniorCompliance(UUID caseId, WorkItemEntity expiredWorkItem) {
        LOG.infof("Escalating expired compliance review for caseId=%s to senior-compliance-officers", caseId);
        workItemService.create(WorkItemCreateRequest.builder()
                .title("ESCALATED: " + expiredWorkItem.title)
                .description("Original compliance review expired (30-day FinCEN SLA breached). "
                        + "Requires immediate attention from head of compliance.\n\n"
                        + expiredWorkItem.description)
                .priority(WorkItemPriority.URGENT)
                .candidateGroups("senior-compliance-officers")
                .createdBy("aml-escalation-system")
                .claimDeadline(Instant.now().plus(7, ChronoUnit.DAYS))
                .callerRef(expiredWorkItem.callerRef)
                .scope("casehubio/aml/oversight")
                .build());
    }
}
