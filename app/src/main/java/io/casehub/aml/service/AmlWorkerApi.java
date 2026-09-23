package io.casehub.aml.service;

import io.casehub.aml.api.model.FlowNode;
import io.casehub.aml.api.model.InvestigationFlowResponse;
import io.casehub.aml.api.model.WorkerTaskResponse;
import io.casehub.aml.api.model.WorkerTaskSubmission;
import io.casehub.aml.engine.AmlInvestigationFlowService;
import io.casehub.aml.query.InvestigationSummaryRepository;
import io.casehub.aml.query.InvestigationSummaryView;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestPath;
import io.casehub.platform.api.mcp.RestStatus;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@McpDomain(value = "aml/workers", basePath = "/api/worker-tasks")
@ApplicationScoped
public class AmlWorkerApi {

    private static final Logger LOG = Logger.getLogger(AmlWorkerApi.class);

    @Inject InvestigationSummaryRepository summaryRepository;
    @Inject AmlInvestigationFlowService flowService;

    @PlatformQuery("List pending worker tasks with optional capability filter")
    @RestPath("/")
    public List<WorkerTaskResponse> listWorkerTasks(String capability) {
        List<InvestigationSummaryView> active = summaryRepository.listByStatus("IN_PROGRESS", 0, 100);
        List<WorkerTaskResponse> tasks = new ArrayList<>();

        for (InvestigationSummaryView summary : active) {
            try {
                InvestigationFlowResponse flow = flowService.getInvestigationFlow(summary.caseId());
                for (int i = 0; i < flow.nodes().size(); i++) {
                    FlowNode node = flow.nodes().get(i);
                    if (!"scheduled".equals(node.status())) continue;
                    if (capability != null && !capability.isBlank() && !capability.equals(node.capabilityTag())) continue;

                    tasks.add(new WorkerTaskResponse(
                            summary.caseId() + ":" + node.capabilityTag(),
                            node.capabilityTag(),
                            summary.caseId().toString(),
                            null,
                            node.timestamp() != null ? node.timestamp().toString() : "",
                            Map.of(),
                            Map.of(
                                    "transactionId", summary.transactionId(),
                                    "flagReason", summary.flagReason(),
                                    "riskScore", summary.riskScore() != null ? summary.riskScore() : 0.0,
                                    "amount", summary.amount(),
                                    "currency", summary.currency(),
                                    "status", summary.status()
                            )
                    ));
                }
            } catch (Exception e) {
                LOG.debugf(e, "Skipping investigation %s — flow data unavailable", summary.caseId());
            }
        }
        return tasks;
    }

    @PlatformMutation("Submit a response to a worker task")
    @RestPath("/{taskId}/respond")
    @RestStatus(202)
    public void respondToTask(@PathParam String taskId, WorkerTaskSubmission submission) {
        // Task ID format: {caseId}:{capabilityTag}
    }
}
