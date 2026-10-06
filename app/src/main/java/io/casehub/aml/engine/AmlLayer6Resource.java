package io.casehub.aml.engine;

import io.casehub.aml.domain.InvestigationResolution;
import io.casehub.aml.domain.InvestigationStatus;
import io.casehub.aml.domain.SarOutcome;
import io.casehub.aml.domain.SuspiciousTransaction;
import io.casehub.aml.trust.AmlWorkerDecisionRepository;
import io.casehub.ledger.api.spi.TrustScoreSource;
import io.casehub.ledger.model.WorkerDecisionEntry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;

@Path("/api/layer6/investigations")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AmlLayer6Resource {

    @Inject
    AmlEngineCoordinator           coordinator;
    @Inject
    AmlWorkerDecisionRepository    workerDecisionRepo;
    @Inject
    Event<SarOutcomeRecordedEvent> sarOutcomeEvent;
    @Inject
    TrustScoreSource               trustScoreSource;
    @Inject
    AmlInvestigationOutcomeService outcomeService;

    @POST
    public Response startInvestigation(final SuspiciousTransaction transaction) {
        final UUID caseId = coordinator.startInvestigation(transaction);
        return Response.accepted(Map.of("caseId", caseId)).build();
    }

    /**
     * Returns the investigation status and routing decisions for a completed case.
     *
     * <p>Completion is determined by the engine's {@code CaseStatus.COMPLETED} flag. The endpoint
     * returns 404 if the caseId has never been used.
     *
     * <p>The {@code trustScore} in each {@link WorkerRoutingDecision} reflects the score from
     * {@link TrustScoreSource} at response time, not the score used at routing time.
     */
    @GET
    @Path("/{caseId}")
    public Response getInvestigation(@PathParam("caseId") UUID caseId) {
        final Optional<InvestigationResolution> resolution =
                outcomeService.resolveInvestigation(caseId);
        if (resolution.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        final InvestigationResolution r = resolution.get();
        if (r.status() != InvestigationStatus.COMPLETED) {
            return Response.ok(new Layer6InvestigationResponse(
                    caseId, r.status(), List.of(), null, r.failureContext())).build();
        }
        final List<WorkerDecisionEntry> entries = workerDecisionRepo.findAllByCaseId(caseId);
        final List<WorkerRoutingDecision> decisions = entries.stream()
                .map(e -> {
                    final OptionalDouble score =
                            trustScoreSource.capabilityScore(e.workerId, e.capabilityTag);
                    return new WorkerRoutingDecision(
                            e.capabilityTag, e.workerId,
                            score.isPresent() ? score.getAsDouble() : null);
                })
                .toList();
        return Response.ok(new Layer6InvestigationResponse(
                caseId, r.status(), decisions, r.outcome(), null)).build();
    }

    @POST
    @Path("/{caseId}/outcome")
    public Response recordOutcome(
            @PathParam("caseId") UUID caseId,
            final SarOutcomeRequest request) {
        final SarOutcome outcome = new SarOutcome(
                request.verdict(), request.reason(), request.investigationAccuracyScore());
        sarOutcomeEvent.fire(new SarOutcomeRecordedEvent(caseId, outcome));
        return Response.noContent().build();
    }
}
