package io.casehub.aml.cbr;

import io.casehub.aml.domain.TriageResult;
import io.casehub.aml.triage.InvestigationTriageEvaluator;
import io.casehub.api.spi.routing.DoublePreference;
import io.casehub.platform.api.preferences.PreferenceKey;
import io.casehub.platform.api.preferences.PreferenceProvider;
import io.casehub.platform.api.preferences.Preferences;
import io.casehub.platform.api.preferences.SettingsScope;
import io.casehub.worker.api.WorkerResult;

import java.util.LinkedHashMap;
import java.util.Map;

final class TriageWorkerSupport {

    private TriageWorkerSupport() {}

    static InvestigationTriageEvaluator buildEvaluator(PreferenceProvider provider) {
        try {
            Preferences prefs = provider.resolve(
                    SettingsScope.of(io.casehub.platform.api.identity.TenancyConstants.DEFAULT_TENANT_ID,
                                     io.casehub.platform.api.path.Path.of("casehubio", "aml", "triage")));
            double sar     = resolve(prefs, AmlTriagePolicyKeys.SAR_THRESHOLD, 0.6);
            double fp      = resolve(prefs, AmlTriagePolicyKeys.FALSE_POSITIVE_THRESHOLD, 0.25);
            double maxAdj  = resolve(prefs, AmlTriagePolicyKeys.MAX_CBR_ADJUSTMENT, 0.15);
            double minConf = resolve(prefs, AmlTriagePolicyKeys.CBR_MIN_CONFIDENCE, 0.3);
            return new InvestigationTriageEvaluator(sar, fp, maxAdj, minConf);
        } catch (Exception e) {
            return new InvestigationTriageEvaluator(0.6, 0.25, 0.15, 0.3);
        }
    }

    static LinkedHashMap<String, Object> toResultMap(TriageResult result) {
        var map = new LinkedHashMap<String, Object>();
        map.put("decision", result.decision().name());
        map.put("reason", result.reason());
        map.put("riskScore", result.riskScore());
        if (result.hardGate() != null) {map.put("hardGate", result.hardGate().name());}
        if (result.cbrThresholdAdjustment() != null) {
            map.put("cbrThresholdAdjustment", result.cbrThresholdAdjustment());
        }
        if (!result.factors().isEmpty()) {
            map.put("factors", result.factors().stream()
                                     .map(f -> Map.<String, Object>of("name", f.name(), "weight", f.weight(), "detail", f.detail()))
                                     .toList());
        }
        return map;
    }

    static WorkerResult toWorkerResult(TriageResult result) {
        return WorkerResult.of(toResultMap(result));
    }

    private static double resolve(Preferences prefs, PreferenceKey<DoublePreference> key, double fallback) {
        var pref = prefs.getOrDefault(key);
        return pref != null ? pref.value() : fallback;
    }
}
