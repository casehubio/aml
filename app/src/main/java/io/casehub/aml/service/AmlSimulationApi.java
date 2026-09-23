package io.casehub.aml.service;

import io.casehub.aml.simulation.AmlScenarioTemplate;
import io.casehub.aml.simulation.AmlSimulationService;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.HttpMethod;
import io.casehub.platform.api.mcp.RestMethod;
import io.casehub.platform.api.mcp.RestPath;
import io.casehub.platform.api.mcp.RestStatus;

import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.UUID;

@McpDomain(value = "aml/simulation", basePath = "/api/simulation")
@ApplicationScoped
@IfBuildProperty(name = "casehub.aml.simulation.enabled", stringValue = "true")
public class AmlSimulationApi {

    private static final Logger LOG = Logger.getLogger(AmlSimulationApi.class);

    @Inject AmlSimulationService simulationService;

    @PlatformMutation("Seed all scenario templates")
    @RestPath("/seed")
    @RestStatus(202)
    public Map<String, Object> seedAll() {
        LOG.info("Seeding all scenarios");
        int count = simulationService.seedAllScenarios();
        return Map.of("seeded", count);
    }

    @PlatformMutation("Seed a single scenario template")
    @RestPath("/seed/{scenario}")
    @RestStatus(202)
    public Map<String, Object> seedScenario(@PathParam String scenario) {
        AmlScenarioTemplate template;
        try {
            template = AmlScenarioTemplate.valueOf(scenario.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new jakarta.ws.rs.WebApplicationException(
                    jakarta.ws.rs.core.Response.status(400)
                            .entity(Map.of("error", "Invalid scenario name: " + scenario))
                            .type(jakarta.ws.rs.core.MediaType.APPLICATION_JSON).build());
        }
        LOG.infof("Seeding scenario: %s", template);
        return simulationService.seedScenario(template)
                .map(caseId -> Map.<String, Object>of("caseId", caseId))
                .orElse(Map.of("message", "Scenario already seeded"));
    }

    @PlatformMutation("Reset all simulation data")
    @RestPath("/seed")
    @RestMethod(HttpMethod.DELETE)
    @RestStatus(204)
    public void resetSimulation() {
        LOG.warn("Resetting simulation data");
        simulationService.resetSimulationData();
    }

    @PlatformMutation("Start a live investigation from a scenario")
    @RestPath("/investigate")
    @RestStatus(202)
    public Map<String, Object> startLiveInvestigation(InvestigationRequest request) {
        if (request == null || request.scenario == null) {
            throw new jakarta.ws.rs.WebApplicationException(
                    jakarta.ws.rs.core.Response.status(400)
                            .entity(Map.of("error", "Missing 'scenario' field"))
                            .type(jakarta.ws.rs.core.MediaType.APPLICATION_JSON).build());
        }
        AmlScenarioTemplate template;
        try {
            template = AmlScenarioTemplate.valueOf(request.scenario.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new jakarta.ws.rs.WebApplicationException(
                    jakarta.ws.rs.core.Response.status(400)
                            .entity(Map.of("error", "Invalid scenario name: " + request.scenario))
                            .type(jakarta.ws.rs.core.MediaType.APPLICATION_JSON).build());
        }
        LOG.infof("Starting live investigation: %s", template);
        UUID caseId = simulationService.startLiveInvestigation(template);
        return Map.of("caseId", caseId);
    }

    @PlatformMutation("Seed the CBR case base with synthetic cases")
    @RestPath("/seed/cbr")
    @RestStatus(202)
    public io.casehub.aml.cbr.SeedResult seedCbr(CbrSeedRequest request) {
        int count = request != null && request.count != null && request.count > 0
                ? request.count : 50;
        LOG.infof("Seeding CBR case base: count=%d", count);
        return simulationService.seedCbrCaseBase(count);
    }

    @PlatformMutation("Clear all CBR cases from the store")
    @RestPath("/seed/cbr")
    @RestMethod(HttpMethod.DELETE)
    @RestStatus(204)
    public void clearCbr() {
        LOG.warn("Clearing CBR case base");
        simulationService.clearCbrCaseBase();
    }

    public static class InvestigationRequest {
        public String scenario;
    }

    public static class CbrSeedRequest {
        public Integer count;
    }
}
