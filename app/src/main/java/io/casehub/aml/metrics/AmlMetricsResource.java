package io.casehub.aml.metrics;

import io.casehub.aml.api.model.GateMetrics;
import io.casehub.aml.api.model.ThroughputMetrics;
import io.casehub.aml.api.model.TrustScoreMetrics;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * REST endpoints for AML investigation metrics.
 * Provides aggregated metrics for the Operations view dashboards.
 */
@Path("/api/metrics")
@Produces(MediaType.APPLICATION_JSON)
public class AmlMetricsResource {

    @Inject
    AmlMetricsService metricsService;

    /**
     * Get throughput metrics for AML investigations.
     * Aggregates from InvestigationSummaryView by status, flag reason, and outcome type.
     *
     * @return throughput metrics
     */
    @GET
    @Path("/throughput")
    public ThroughputMetrics getThroughputMetrics() {
        return metricsService.getThroughputMetrics();
    }

    /**
     * Get trust score metrics for all known AML agents.
     * Fetches current trust scores from TrustScoreSource for each agent/capability pair.
     *
     * @return trust score metrics
     */
    @GET
    @Path("/trust-scores")
    public TrustScoreMetrics getTrustScoreMetrics() {
        return metricsService.getTrustScoreMetrics();
    }

    /**
     * Get oversight gate metrics for AML investigations.
     * Aggregates from WorkItems with callerRef pattern matching gates.
     *
     * @return gate metrics
     */
    @GET
    @Path("/gates")
    public GateMetrics getGateMetrics() {
        return metricsService.getGateMetrics();
    }
}
