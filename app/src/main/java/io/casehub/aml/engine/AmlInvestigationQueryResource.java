package io.casehub.aml.engine;

import io.casehub.aml.api.model.InvestigationFindingsResponse;
import io.casehub.aml.api.model.InvestigationFlowResponse;
import io.casehub.aml.domain.InvestigationSummaryResponse;
import io.casehub.aml.domain.PagedResponse;
import io.casehub.aml.query.InvestigationSummaryRepository;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

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
    public CompletionStage<InvestigationFlowResponse> getInvestigationFlow(@PathParam("caseId") UUID caseId) {
        return flowService.getInvestigationFlow(caseId);
    }

    @GET
    @Path("/{caseId}/findings")
    public CompletionStage<InvestigationFindingsResponse> getFindings(@PathParam("caseId") UUID caseId) {
        return findingsService.getFindings(caseId);
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
                view.createdAt()
        );
    }
}
