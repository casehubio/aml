package io.casehub.aml.engine;

import io.casehub.aml.domain.InvestigationResolution;
import io.casehub.aml.domain.SuspiciousTransaction;
import io.casehub.aml.query.InvestigationSummaryRepository;
import io.casehub.api.engine.CaseHubRuntime;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Layer 9 REST resource — oversight gate investigation.
 *
 * <p>Completion is determined by the engine's {@code CaseStatus.COMPLETED} flag. The endpoint
 * returns 404 if the caseId has never been used.
 */
@Path("/api/layer9/investigations")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AmlLayer9Resource {

    @Inject
    AmlOversightCoordinator coordinator;
    @Inject
    AmlInvestigationOutcomeService outcomeService;
    @Inject
    InvestigationSummaryRepository investigationSummaryRepository;
    @Inject
    CaseHubRuntime caseHubRuntime;

    @POST
    public Response startInvestigation(final SuspiciousTransaction transaction) {
        final UUID caseId = coordinator.startInvestigation(transaction);
        return Response.accepted(Map.of("caseId", caseId)).build();
    }

    @GET
    @Path("/{caseId}")
    public Response getInvestigation(@PathParam("caseId") final UUID caseId) {
        final Optional<InvestigationResolution> resolution =
                outcomeService.resolveInvestigation(caseId);
        if (resolution.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        final InvestigationResolution r = resolution.get();
        return Response.ok(new Layer9InvestigationResponse(
                caseId, r.status(), r.outcome(), r.failureContext())).build();
    }

    @POST
    @Path("/{caseId}/suspend")
    @Consumes(MediaType.WILDCARD)
    public Response suspendInvestigation(@PathParam("caseId") final UUID caseId) {
        try {
            caseHubRuntime.suspendCase(caseId);
        } catch (final RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("not found")) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            return Response.status(Response.Status.CONFLICT)
                    .entity(Map.of("error", e.getMessage())).build();
        }
        return Response.noContent().build();
    }

    @POST
    @Path("/{caseId}/resume")
    @Consumes(MediaType.WILDCARD)
    public Response resumeInvestigation(@PathParam("caseId") final UUID caseId) {
        try {
            caseHubRuntime.resumeCase(caseId);
        } catch (final RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("not found")) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            return Response.status(Response.Status.CONFLICT)
                    .entity(Map.of("error", e.getMessage())).build();
        }
        return Response.noContent().build();
    }
}
