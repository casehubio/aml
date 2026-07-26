package io.casehub.aml.cbr;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.aml.domain.AmlActionType;
import io.casehub.aml.domain.CbrPathAdvice;
import io.casehub.aml.domain.EntityResolutionResult;
import io.casehub.aml.domain.OsintResult;
import io.casehub.aml.domain.PatternAnalysisResult;
import io.casehub.aml.domain.TriageDecision;
import io.casehub.aml.domain.TriageInput;
import io.casehub.aml.domain.TriageResult;
import io.casehub.aml.triage.InvestigationTriageEvaluator;
import io.casehub.api.spi.routing.DoublePreference;
import io.casehub.platform.api.preferences.PreferenceKey;
import io.casehub.platform.api.preferences.PreferenceProvider;
import io.casehub.platform.api.preferences.Preferences;
import io.casehub.platform.api.preferences.SettingsScope;
import io.casehub.worker.api.PlannedAction;
import io.casehub.worker.api.Worker;
import io.casehub.worker.api.WorkerResult;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public final class InvestigationTriageWorker {

    private InvestigationTriageWorker() {}

    public static Worker create(ObjectMapper objectMapper, PreferenceProvider preferenceProvider) {
        return Worker.builder()
                     .name("investigation-triage-agent")
                     .capabilityName("investigation-triage")
                     .function((Map<String, Object> input) -> {
                         var triageInput = deserializeInput(objectMapper, input);
                         var evaluator = buildEvaluator(preferenceProvider);
                         var result = evaluator.evaluate(triageInput);
                         return toWorkerResult(result);
                     })
                     .build();
    }

    private static TriageInput deserializeInput(ObjectMapper mapper, Map<String, Object> input) {
        var entity = mapper.convertValue(input.get("entityResolution"), EntityResolutionResult.class);
        var pattern = mapper.convertValue(input.get("patternAnalysis"), PatternAnalysisResult.class);
        var osint = mapper.convertValue(input.get("osintScreening"), OsintResult.class);
        CbrPathAdvice cbr = input.get("cbrPathAdvice") != null
                ? mapper.convertValue(input.get("cbrPathAdvice"), CbrPathAdvice.class) : null;
        return new TriageInput(entity, pattern, osint, cbr);
    }

    private static InvestigationTriageEvaluator buildEvaluator(PreferenceProvider provider) {
        try {
            Preferences prefs = provider.resolve(
                    SettingsScope.of(io.casehub.platform.api.identity.TenancyConstants.DEFAULT_TENANT_ID, io.casehub.platform.api.path.Path.of("casehubio", "aml", "triage")));
            double sar = resolve(prefs, AmlTriagePolicyKeys.SAR_THRESHOLD, 0.6);
            double fp = resolve(prefs, AmlTriagePolicyKeys.FALSE_POSITIVE_THRESHOLD, 0.25);
            double maxAdj = resolve(prefs, AmlTriagePolicyKeys.MAX_CBR_ADJUSTMENT, 0.15);
            double minConf = resolve(prefs, AmlTriagePolicyKeys.CBR_MIN_CONFIDENCE, 0.3);
            return new InvestigationTriageEvaluator(sar, fp, maxAdj, minConf);
        } catch (Exception e) {
            return new InvestigationTriageEvaluator(0.6, 0.25, 0.15, 0.3);
        }
    }

    private static double resolve(Preferences prefs,
                                   PreferenceKey<DoublePreference> key,
                                   double fallback) {
        var pref = prefs.getOrDefault(key);
        return pref != null ? pref.value() : fallback;
    }

    private static WorkerResult toWorkerResult(TriageResult result) {
        var map = new LinkedHashMap<String, Object>();
        map.put("decision", result.decision().name());
        map.put("reason", result.reason());
        map.put("riskScore", result.riskScore());
        if (result.hardGate() != null) map.put("hardGate", result.hardGate().name());
        if (result.cbrThresholdAdjustment() != null) map.put("cbrThresholdAdjustment", result.cbrThresholdAdjustment());
        if (!result.factors().isEmpty()) {
            map.put("factors", result.factors().stream()
                    .map(f -> Map.<String, Object>of("name", f.name(), "weight", f.weight(), "detail", f.detail()))
                    .toList());
        }

        if (result.decision() == TriageDecision.INCONCLUSIVE) {
            return WorkerResult.of(map, PlannedAction.of(
                    "Investigation clearance — inconclusive evidence requires compliance review",
                    AmlActionType.INVESTIGATION_CLEARANCE.actionType(),
                    Map.of(
                            "riskScore", String.valueOf(result.riskScore()),
                            "reason", result.reason(),
                            "factors", result.factors().stream()
                                    .map(f -> f.name() + "=" + f.weight())
                                    .collect(Collectors.joining(", ")))));
        }
        return WorkerResult.of(map);
    }
}
