package io.casehub.aml.service;

import io.casehub.aml.domain.InvestigationResolution;
import io.casehub.aml.domain.InvestigationStatus;
import io.casehub.aml.domain.SarOutcome;
import io.casehub.aml.domain.SuspiciousTransaction;
import io.casehub.aml.engine.AmlEngineCoordinator;
import io.casehub.aml.engine.AmlInvestigationOutcomeService;
import io.casehub.aml.engine.AmlOversightCoordinator;
import io.casehub.aml.engine.Layer5InvestigationResponse;
import io.casehub.aml.engine.Layer6InvestigationResponse;
import io.casehub.aml.engine.Layer9InvestigationResponse;
import io.casehub.aml.engine.SarOutcomeRecordedEvent;
import io.casehub.aml.engine.SarOutcomeRequest;
import io.casehub.aml.engine.WorkerRoutingDecision;
import io.casehub.aml.trust.AmlWorkerDecisionRepository;
import io.casehub.api.engine.CaseHubRuntime;
import io.casehub.ledger.api.spi.TrustScoreSource;
import io.casehub.ledger.model.WorkerDecisionEntry;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestPath;
import io.casehub.platform.api.mcp.RestStatus;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;

@McpDomain(value = "aml/engine", app = "aml", basePath = "/api")
@ApplicationScoped
public class AmlEngineApi {

    @Inject AmlEngineCoordinator coordinator;
    @Inject AmlOversightCoordinator oversightCoordinator;
    @Inject AmlInvestigationOutcomeService outcomeService;
    @Inject AmlWorkerDecisionRepository workerDecisionRepo;
    @Inject Event<SarOutcomeRecordedEvent> sarOutcomeEvent;
    @Inject TrustScoreSource trustScoreSource;
    @Inject CaseHubRuntime caseHubRuntime;

    @PlatformMutation("Start Layer 5 investigation")
    @RestPath("/layer5/investigations")
    @RestStatus(200)
    public Layer5InvestigationResponse startLayer5Investigation(SuspiciousTransaction transaction) {
        UUID caseId = coordinator.startInvestigation(transaction);
        return new Layer5InvestigationResponse(caseId, "started");
    }

    @PlatformMutation("Start Layer 6 investigation")
    @RestPath("/layer6/investigations")
    @RestStatus(202)
    public Map<String, Object> startLayer6Investigation(SuspiciousTransaction transaction) {
        UUID caseId = coordinator.startInvestigation(transaction);
        return Map.of("caseId", caseId);
    }

    @PlatformQuery("Get Layer 6 investigation status and routing decisions")
    @RestPath("/layer6/investigations/{caseId}")
    public Layer6InvestigationResponse getLayer6Investigation(@PathParam UUID caseId) {
        Optional<InvestigationResolution> resolution = outcomeService.resolveInvestigation(caseId);
        if (resolution.isEmpty()) {
            throw new NotFoundException("Investigation not found: " + caseId);
        }
        InvestigationResolution r = resolution.get();
        if (r.status() != InvestigationStatus.COMPLETED) {
            return new Layer6InvestigationResponse(
                    caseId, r.status(), List.of(), null, r.failureContext());
        }
        List<WorkerDecisionEntry> entries = workerDecisionRepo.findAllByCaseId(caseId);
        List<WorkerRoutingDecision> decisions = entries.stream()
                .map(e -> {
                    OptionalDouble score = trustScoreSource.capabilityScore(e.workerId, e.capabilityTag);
                    return new WorkerRoutingDecision(
                            e.capabilityTag, e.workerId,
                            score.isPresent() ? score.getAsDouble() : null);
                })
                .toList();
        return new Layer6InvestigationResponse(
                caseId, r.status(), decisions, r.outcome(), null);
    }

    @PlatformMutation("Record SAR outcome for an investigation")
    @RestPath("/layer6/investigations/{caseId}/outcome")
    @RestStatus(204)
    public void recordOutcome(@PathParam UUID caseId, SarOutcomeRequest request) {
        SarOutcome outcome = new SarOutcome(
                request.verdict(), request.reason(), request.investigationAccuracyScore());
        sarOutcomeEvent.fire(new SarOutcomeRecordedEvent(caseId, outcome));
    }

    @PlatformMutation("Start Layer 9 oversight investigation")
    @RestPath("/layer9/investigations")
    @RestStatus(202)
    public Map<String, Object> startLayer9Investigation(SuspiciousTransaction transaction) {
        UUID caseId = oversightCoordinator.startInvestigation(transaction);
        return Map.of("caseId", caseId);
    }

    @PlatformQuery("Get Layer 9 investigation status")
    @RestPath("/layer9/investigations/{caseId}")
    public Layer9InvestigationResponse getLayer9Investigation(@PathParam UUID caseId) {
        Optional<InvestigationResolution> resolution = outcomeService.resolveInvestigation(caseId);
        if (resolution.isEmpty()) {
            throw new NotFoundException("Investigation not found: " + caseId);
        }
        InvestigationResolution r = resolution.get();
        return new Layer9InvestigationResponse(caseId, r.status(), r.outcome(), r.failureContext());
    }

}
