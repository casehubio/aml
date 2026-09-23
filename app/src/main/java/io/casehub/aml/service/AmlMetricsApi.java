package io.casehub.aml.service;

import io.casehub.aml.api.model.GateMetrics;
import io.casehub.aml.api.model.InterventionMetrics;
import io.casehub.aml.api.model.ThroughputMetrics;
import io.casehub.aml.api.model.TrustScoreMetrics;
import io.casehub.aml.api.model.TrustScoreSnapshotResponse;
import io.casehub.aml.metrics.AmlMetricsService;
import io.casehub.aml.metrics.SarQualityService;
import io.casehub.aml.quality.SarQualityReport;
import io.casehub.aml.rest.BootstrapReport;
import io.casehub.aml.trust.TrustScoreSnapshotService;
import io.casehub.aml.cbr.AmlCbrPolicyKeys;
import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestPath;
import io.casehub.platform.api.preferences.PreferenceProvider;
import io.casehub.platform.api.preferences.SettingsScope;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@McpDomain(value = "aml/metrics", app = "aml", basePath = "/api")
@ApplicationScoped
public class AmlMetricsApi {

    @Inject AmlMetricsService metricsService;
    @Inject TrustScoreSnapshotService snapshotService;
    @Inject SarQualityService sarQualityService;
    @PersistenceContext(unitName = "qhorus") EntityManager em;
    @Inject PreferenceProvider preferenceProvider;

    @PlatformQuery("Get throughput metrics for AML investigations")
    @RestPath("/metrics/throughput")
    public ThroughputMetrics getThroughputMetrics() {
        return metricsService.getThroughputMetrics();
    }

    @PlatformQuery("Get trust score metrics for AML agents")
    @RestPath("/metrics/trust-scores")
    public TrustScoreMetrics getTrustScoreMetrics() {
        return metricsService.getTrustScoreMetrics();
    }

    @PlatformQuery("Get oversight gate metrics")
    @RestPath("/metrics/gates")
    public GateMetrics getGateMetrics() {
        return metricsService.getGateMetrics();
    }

    @PlatformQuery("Get historical trust score snapshots")
    @RestPath("/metrics/trust-scores/history")
    public List<TrustScoreSnapshotResponse> getTrustScoreHistory(String agentId, String capability) {
        return snapshotService.getHistory(agentId, capability).stream()
                .map(s -> new TrustScoreSnapshotResponse(
                        s.id(), s.agentId(), s.capability(),
                        s.alpha(), s.beta(), s.score(), s.snapshotTimestamp()))
                .toList();
    }

    @PlatformQuery("Get intervention metrics")
    @RestPath("/metrics/interventions")
    public InterventionMetrics getInterventionMetrics() {
        return metricsService.getInterventionMetrics();
    }

    @PlatformQuery("Get SAR quality report")
    @RestPath("/metrics/sar-quality")
    public SarQualityReport getSarQualityMetrics() {
        return sarQualityService.generateReport();
    }

    @PlatformQuery("Get CBR bootstrap report")
    @RestPath("/cbr/bootstrap-report")
    public BootstrapReport getBootstrapReport() {
        return new BootstrapReport(buildCaseBaseSummary(), buildAdvisoryMetrics());
    }

    private BootstrapReport.CaseBaseSummary buildCaseBaseSummary() {
        long total = em.createQuery(
                "SELECT COUNT(e) FROM AmlCaseProfileLedgerEntry e", Long.class)
                .getSingleResult();
        return new BootstrapReport.CaseBaseSummary(
                total, resolveThreshold(),
                groupBy("flagReason"), groupBy("entityType"),
                groupBy("jurisdictionRisk"), groupBy("outcome"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Long> groupBy(String field) {
        var results = em.createQuery(
                "SELECT e." + field + ", COUNT(e) FROM AmlCaseProfileLedgerEntry e " +
                "WHERE e." + field + " IS NOT NULL GROUP BY e." + field)
                .getResultList();
        var map = new LinkedHashMap<String, Long>();
        for (var row : results) {
            var arr = (Object[]) row;
            map.put((String) arr[0], (Long) arr[1]);
        }
        return map;
    }

    private BootstrapReport.AdvisoryMetrics buildAdvisoryMetrics() {
        long total = em.createQuery(
                "SELECT COUNT(e) FROM AmlCbrAdvisoryLedgerEntry e", Long.class)
                .getSingleResult();
        if (total == 0) {
            return new BootstrapReport.AdvisoryMetrics(0, 0, 0, 0.0, 0.0);
        }
        long activeCount = em.createQuery(
                "SELECT COUNT(e) FROM AmlCbrAdvisoryLedgerEntry e WHERE e.active = true", Long.class)
                .getSingleResult();
        double avgConf = em.createQuery(
                "SELECT AVG(e.confidence) FROM AmlCbrAdvisoryLedgerEntry e", Double.class)
                .getSingleResult();
        double avgCount = em.createQuery(
                "SELECT AVG(e.caseCount) FROM AmlCbrAdvisoryLedgerEntry e", Double.class)
                .getSingleResult();
        return new BootstrapReport.AdvisoryMetrics(
                total, activeCount, total - activeCount, avgConf, avgCount);
    }

    private int resolveThreshold() {
        try {
            var prefs = preferenceProvider.resolve(
                    SettingsScope.of(TenancyConstants.DEFAULT_TENANT_ID,
                            io.casehub.platform.api.path.Path.of("casehubio", "aml", "cbr")));
            var pref = prefs.getOrDefault(AmlCbrPolicyKeys.ACTIVATION_THRESHOLD);
            return pref != null ? (int) pref.value() : 30;
        } catch (Exception e) {
            return 30;
        }
    }
}
