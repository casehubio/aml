package io.casehub.aml.metrics;

import io.casehub.aml.api.model.GateMetrics;
import io.casehub.aml.api.model.ThroughputMetrics;
import io.casehub.aml.api.model.TrustScoreMetrics;
import io.casehub.aml.api.model.TrustScoreSnapshotResponse;
import io.casehub.aml.trust.TrustScoreSnapshotService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/**
 * REST endpoints for AML investigation metrics.
 * Provides aggregated metrics for the Operations view dashboards.
 */
@Path("/api/metrics")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class AmlMetricsResource {

    @Inject
    AmlMetricsService metricsService;
    @Inject
    TrustScoreSnapshotService snapshotService;
    @Inject
    SarQualityService         sarQualityService;


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

    /**
     * Get historical trust score snapshots for a specific agent/capability pair.
     * Returns snapshots ordered by timestamp ascending for trend display.
     *
     * @param agentId    agent identifier (e.g. "sar-drafting-agent-senior")
     * @param capability capability tag (e.g. "sar-drafting")
     * @return list of trust score snapshots
     */
    @GET
    @Path("/trust-scores/history")
    public List<TrustScoreSnapshotResponse> getTrustScoreHistory(
            @QueryParam("agentId") String agentId,
            @QueryParam("capability") String capability) {
        return snapshotService.getHistory(agentId, capability).stream()
                              .map(s -> new TrustScoreSnapshotResponse(
                                      s.id(), s.agentId(), s.capability(),
                                      s.alpha(), s.beta(), s.score(), s.snapshotTimestamp()))
                              .toList();
    }

    @jakarta.ws.rs.GET
    @jakarta.ws.rs.Path("/sar-quality")
    public io.casehub.aml.quality.SarQualityReport getSarQualityMetrics() {
        return sarQualityService.generateReport();
    }

}
