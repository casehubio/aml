package io.casehub.aml.engine;

import io.casehub.aml.api.model.FlowNode;
import io.casehub.aml.api.model.InvestigationFlowResponse;
import io.casehub.aml.api.model.WorkerTaskResponse;
import io.casehub.aml.api.model.WorkerTaskSubmission;
import io.casehub.aml.query.InvestigationSummaryRepository;
import io.casehub.aml.query.InvestigationSummaryView;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Path("/api/worker-tasks")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AmlWorkerTaskResource {

    @Inject InvestigationSummaryRepository summaryRepository;
    @Inject AmlInvestigationFlowService flowService;

    @GET
    public List<WorkerTaskResponse> listWorkerTasks(
            @QueryParam("capability") String capability) {

        List<InvestigationSummaryView> active = summaryRepository.listByStatus("IN_PROGRESS", Page.ofSize(100));
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
                // Skip investigations where flow data is unavailable
            }
        }
        return tasks;
    }

    @POST
    @Path("/{taskId}/respond")
    public Response respondToTask(@PathParam("taskId") String taskId, WorkerTaskSubmission submission) {
        // Task ID format: {caseId}:{capabilityTag}
        // In a full implementation, this would submit a qhorus RESPONSE/DONE/DECLINE message
        return Response.accepted().build();
    }
}
