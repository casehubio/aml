package io.casehub.aml.engine;

import io.casehub.aml.api.model.InvestigationFindingsResponse;
import io.casehub.aml.api.model.InvestigationFlowResponse;
import io.casehub.aml.api.model.InvestigationRoutingResponse;
import io.casehub.aml.domain.InvestigationSummaryResponse;
import io.casehub.aml.domain.PagedResponse;
import io.casehub.aml.query.InvestigationSummaryRepository;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/api/investigations")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AmlInvestigationQueryResource {

    @Inject
    InvestigationSummaryRepository repository;

    @Inject
    AmlInvestigationPriorContextService priorContextService;

    @Inject
    AmlInvestigationFlowService flowService;

    @Inject
    AmlInvestigationFindingsService findingsService;

    @Inject
    AmlInvestigationGatesService gatesService;

    @Inject
    AmlInvestigationRoutingService routingService;

    @GET
    public PagedResponse<InvestigationSummaryResponse> listInvestigations(
            @QueryParam("status") String status,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("pageSize") @DefaultValue("25") int pageSize) {

        Page panachePage = Page.of(page, pageSize);

        List<InvestigationSummaryResponse> items;
        long total;

        if (status != null && !status.isBlank()) {
            items = repository.listByStatus(status, panachePage)
                    .stream()
                    .map(this::toResponse)
                    .toList();
            total = repository.countByStatus(status);
        } else {
            items = repository.listAll(panachePage)
                    .stream()
                    .map(this::toResponse)
                    .toList();
            total = repository.count();
        }

        return new PagedResponse<>(items, total, page, pageSize);
    }

    @GET
    @Path("/{caseId}/prior-context")
    public Map<String, Object> getPriorContext(@PathParam("caseId") UUID caseId) {
        return priorContextService.getPriorContext(caseId);
    }

    @GET
    @Path("/{caseId}/flow")
    public InvestigationFlowResponse getInvestigationFlow(@PathParam("caseId") UUID caseId) {
        return flowService.getInvestigationFlow(caseId);
    }

    @GET
    @Path("/{caseId}/findings")
    public InvestigationFindingsResponse getFindings(@PathParam("caseId") UUID caseId) {
        return findingsService.getFindings(caseId);
    }

    @GET
    @Path("/{caseId}/gates")
    public io.casehub.aml.api.model.InvestigationGatesResponse getGates(@PathParam("caseId") UUID caseId) {
        return gatesService.getGates(caseId);
    }

    @GET
    @Path("/{caseId}/routing")
    public InvestigationRoutingResponse getRouting(@PathParam("caseId") UUID caseId) {
        return routingService.getRoutingDecisions(caseId);
    }

    private InvestigationSummaryResponse toResponse(io.casehub.aml.query.InvestigationSummaryView view) {
        return new InvestigationSummaryResponse(
                view.caseId(),
                view.status(),
                view.outcomeType(),
                view.transactionId(),
                view.originAccount(),
                view.destinationAccount(),
                view.amount(),
                view.currency(),
                view.flagReason(),
                view.riskScore(),
                view.createdAt()
        );
    }
}
