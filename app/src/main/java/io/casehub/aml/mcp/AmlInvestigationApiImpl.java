package io.casehub.aml.mcp;

import io.casehub.aml.api.mcp.AmlInvestigationApi;
import io.casehub.aml.api.mcp.InvestigationDetail;
import io.casehub.aml.api.mcp.StalledInvestigation;
import io.casehub.aml.compliance.InvestigationStallDetector;
import io.casehub.aml.engine.AmlInvestigationFindingsService;
import io.casehub.aml.engine.AmlInvestigationGatesService;
import io.casehub.aml.engine.AmlInvestigationOutcomeService;
import io.casehub.aml.engine.AmlInvestigationRoutingService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class AmlInvestigationApiImpl implements AmlInvestigationApi {

    @Inject AmlInvestigationOutcomeService outcomeService;
    @Inject AmlInvestigationFindingsService findingsService;
    @Inject AmlInvestigationGatesService gatesService;
    @Inject AmlInvestigationRoutingService routingService;
    @Inject InvestigationStallDetector stallDetector;

    @Override
    public InvestigationDetail getInvestigation(UUID caseId) {
        return outcomeService.resolveInvestigation(caseId)
                             .map(r -> new InvestigationDetail(
                                     caseId,
                                     r.status().name(),
                                     r.outcome() != null ? r.outcome().type() : null,
                                     findingsService.getFindings(caseId),
                                     gatesService.getGates(caseId),
                                     routingService.getRoutingDecisions(caseId)))
                             .orElse(null);}

    @Override
    public List<StalledInvestigation> listStalled() {
        return stallDetector.detectStalled().stream()
                .map(s -> new StalledInvestigation(s.caseId(), s.waitingForWorkId(), s.stalledFor()))
                .toList();
    }
}
