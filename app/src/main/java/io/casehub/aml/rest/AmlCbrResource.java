package io.casehub.aml.rest;

import io.casehub.aml.cbr.AmlCbrPolicyKeys;
import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.platform.api.preferences.PreferenceProvider;
import io.casehub.platform.api.preferences.SettingsScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.LinkedHashMap;
import java.util.Map;

@Path("/api/cbr")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class AmlCbrResource {

    @PersistenceContext(unitName = "qhorus")
    EntityManager em;

    @Inject
    PreferenceProvider preferenceProvider;

    @GET
    @Path("/bootstrap-report")
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
