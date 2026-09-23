package io.casehub.aml.service;

import io.casehub.aml.AmlInvestigationApplicationService;
import io.casehub.aml.api.model.InvestigationFindingsResponse;
import io.casehub.aml.api.model.InvestigationFlowResponse;
import io.casehub.aml.api.model.InvestigationRoutingResponse;
import io.casehub.aml.domain.AmlInvestigationResult;
import io.casehub.aml.domain.InvestigationSummaryResponse;
import io.casehub.aml.domain.PagedResponse;
import io.casehub.aml.domain.SuspiciousTransaction;
import io.casehub.aml.engine.AmlInvestigationFindingsService;
import io.casehub.aml.engine.AmlInvestigationFlowService;
import io.casehub.aml.engine.AmlInvestigationGatesService;
import io.casehub.aml.engine.AmlInvestigationPriorContextService;
import io.casehub.aml.engine.AmlInvestigationRoutingService;
import io.casehub.aml.query.InvestigationSummaryRepository;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PaginatedResponse;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestPath;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@McpDomain(value = "aml/investigations", app = "aml", basePath = "/api/investigations")
@ApplicationScoped
public class AmlInvestigationApi {

    @Inject AmlInvestigationApplicationService investigationService;
    @Inject InvestigationSummaryRepository summaryRepository;
    @Inject AmlInvestigationPriorContextService priorContextService;
    @Inject AmlInvestigationFlowService flowService;
    @Inject AmlInvestigationFindingsService findingsService;
    @Inject AmlInvestigationGatesService gatesService;
    @Inject AmlInvestigationRoutingService routingService;

    @PlatformMutation("Start a new AML investigation")
    @RestPath("/")
    public AmlInvestigationResult investigate(SuspiciousTransaction transaction) {
        return investigationService.investigate(transaction);
    }

    @PlatformQuery("List investigations with optional status filter")
    @PaginatedResponse(totalCountMethod = "total")
    @RestPath("/")
    public PagedResponse<InvestigationSummaryResponse> listInvestigations(
            String status, Integer pageIndex, Integer pageSize) {
        int p = pageIndex != null ? pageIndex : 0;
        int ps = pageSize != null ? pageSize : 25;
        int offset = p * ps;

        List<InvestigationSummaryResponse> items;
        long total;

        if (status != null && !status.isBlank()) {
            items = summaryRepository.listByStatus(status, offset, ps).stream()
                    .map(this::toResponse).toList();
            total = summaryRepository.countByStatus(status);
        } else {
            items = summaryRepository.listAll(offset, ps).stream()
                    .map(this::toResponse).toList();
            total = summaryRepository.count();
        }
        return new PagedResponse<>(items, total, pageIndex != null ? pageIndex : 0, ps);
    }

    @PlatformQuery("Get prior context for an investigation")
    @RestPath("/{caseId}/prior-context")
    public Map<String, Object> getPriorContext(@PathParam UUID caseId) {
        return priorContextService.getPriorContext(caseId);
    }

    @PlatformQuery("Get investigation execution flow")
    @RestPath("/{caseId}/flow")
    public InvestigationFlowResponse getInvestigationFlow(@PathParam UUID caseId) {
        return flowService.getInvestigationFlow(caseId);
    }

    @PlatformQuery("Get investigation findings")
    @RestPath("/{caseId}/findings")
    public InvestigationFindingsResponse getFindings(@PathParam UUID caseId) {
        return findingsService.getFindings(caseId);
    }

    @PlatformQuery("Get investigation gate decisions")
    @RestPath("/{caseId}/gates")
    public io.casehub.aml.api.model.InvestigationGatesResponse getGates(@PathParam UUID caseId) {
        return gatesService.getGates(caseId);
    }

    @PlatformQuery("Get investigation routing decisions")
    @RestPath("/{caseId}/routing")
    public InvestigationRoutingResponse getRouting(@PathParam UUID caseId) {
        return routingService.getRoutingDecisions(caseId);
    }

    private InvestigationSummaryResponse toResponse(io.casehub.aml.query.InvestigationSummaryView view) {
        return new InvestigationSummaryResponse(
                view.caseId(), view.status(), view.outcomeType(),
                view.transactionId(), view.originAccount(), view.destinationAccount(),
                view.amount(), view.currency(), view.flagReason(),
                view.riskScore(), view.createdAt());
    }
}
