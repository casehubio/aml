package io.casehub.aml.simulation;

import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.UUID;

/**
 * REST API for AML investigation simulation and demo seeding.
 *
 * <p><strong>Build-time gating:</strong> Only available when
 * {@code casehub.aml.simulation.enabled=true}. In production builds, these
 * endpoints do not exist.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /api/simulation/seed} — runs all scenarios (202 Accepted)</li>
 *   <li>{@code POST /api/simulation/seed/{scenario}} — runs one scenario (202 Accepted)</li>
 *   <li>{@code DELETE /api/simulation/seed} — full data reset (204 No Content)</li>
 *   <li>{@code POST /api/simulation/investigate} — starts live investigation (returns caseId)</li>
 * </ul>
 */
@Path("/api/simulation")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@IfBuildProperty(name = "casehub.aml.simulation.enabled", stringValue = "true")
public class AmlSimulationResource {

    private static final Logger LOG = Logger.getLogger(AmlSimulationResource.class);

    @Inject AmlSimulationService simulationService;

    /**
     * Seed all scenario templates.
     * <p>
     * Idempotent: skips scenarios whose {@code transactionId} already exists.
     *
     * @return 202 Accepted with count of scenarios seeded
     */
    @POST
    @Path("/seed")
    public Response seedAll() {
        LOG.info("Seeding all scenarios");
        final int count = simulationService.seedAllScenarios();
        return Response.accepted(Map.of("seeded", count)).build();
    }

    /**
     * Seed a single scenario template.
     * <p>
     * Idempotent: returns 200 OK with existing caseId if the scenario was already seeded.
     *
     * @param scenarioName scenario template name (e.g. "PEP", "STRUCTURING")
     * @return 202 Accepted with caseId if seeded; 200 OK with existing caseId if already seeded
     */
    @POST
    @Path("/seed/{scenario}")
    public Response seedScenario(@PathParam("scenario") final String scenarioName) {
        final AmlScenarioTemplate template;
        try {
            template = AmlScenarioTemplate.valueOf(scenarioName.toUpperCase());
        } catch (final IllegalArgumentException e) {
            LOG.warnf("Invalid scenario name: %s", scenarioName);
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "Invalid scenario name: " + scenarioName))
                .build();
        }

        LOG.infof("Seeding scenario: %s", template);
        return simulationService.seedScenario(template)
            .map(caseId -> Response.accepted(Map.of("caseId", caseId)).build())
            .orElseGet(() -> {
                LOG.debugf("Scenario %s already seeded", template);
                return Response.ok(Map.of("message", "Scenario already seeded")).build();
            });
    }

    /**
     * Reset all simulation data.
     * <p>
     * <strong>WARNING:</strong> Truncates {@code aml_investigation_summary}. Breaks
     * Merkle chain integrity — acceptable in simulation mode only.
     *
     * @return 204 No Content
     */
    @DELETE
    @Path("/seed")
    public Response resetSimulation() {
        LOG.warn("Resetting simulation data");
        simulationService.resetSimulationData();
        return Response.noContent().build();
    }

    /**
     * Start a live investigation from a scenario template.
     * <p>
     * Unlike {@code POST /seed/{scenario}}, this always generates a unique transaction ID,
     * allowing multiple runs of the same scenario for demo purposes.
     *
     * @param request JSON body with {@code scenario} field
     * @return 202 Accepted with caseId
     */
    @POST
    @Path("/investigate")
    public Response startLiveInvestigation(final InvestigationRequest request) {
        if (request.scenario == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "Missing 'scenario' field"))
                .build();
        }

        final AmlScenarioTemplate template;
        try {
            template = AmlScenarioTemplate.valueOf(request.scenario.toUpperCase());
        } catch (final IllegalArgumentException e) {
            LOG.warnf("Invalid scenario name: %s", request.scenario);
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "Invalid scenario name: " + request.scenario))
                .build();
        }

        LOG.infof("Starting live investigation: %s", template);
        final UUID caseId = simulationService.startLiveInvestigation(template);
        return Response.accepted(Map.of("caseId", caseId)).build();
    }

    /**
     * Request body for {@code POST /api/simulation/investigate}.
     */
    public static class InvestigationRequest {
        public String scenario;
    }

    /**
     * Seed the CBR case base with synthetic cases for development and demo.
     *
     * @param request optional body with {@code count} field (default: 50)
     * @return 202 Accepted with seed coverage report
     */
    @POST
    @Path("/seed/cbr")
    public Response seedCbr(final CbrSeedRequest request) {
        final int count = request != null && request.count != null && request.count > 0
                          ? request.count : 50;
        LOG.infof("Seeding CBR case base: count=%d", count);
        final io.casehub.aml.cbr.SeedResult result = simulationService.seedCbrCaseBase(count);
        return Response.accepted(result).build();
    }

    /**
     * Clear all CBR cases from the store.
     *
     * @return 204 No Content
     */
    @DELETE
    @Path("/seed/cbr")
    public Response clearCbr() {
        LOG.warn("Clearing CBR case base");
        simulationService.clearCbrCaseBase();
        return Response.noContent().build();
    }

    /**
     * Request body for {@code POST /api/simulation/seed/cbr}.
     */
    public static class CbrSeedRequest {
        public Integer count;
    }

}
