package io.casehub.aml.cbr;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.aml.domain.AmlActionType;
import io.casehub.aml.domain.CbrPathAdvice;
import io.casehub.aml.domain.EntityResolutionResult;
import io.casehub.aml.domain.OsintResult;
import io.casehub.aml.domain.PatternAnalysisResult;
import io.casehub.aml.domain.TriageDecision;
import io.casehub.aml.domain.TriageInput;
import io.casehub.platform.api.preferences.PreferenceProvider;
import io.casehub.worker.api.PlannedAction;
import io.casehub.worker.api.Worker;
import io.casehub.worker.api.WorkerResult;

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
                         var evaluator   = TriageWorkerSupport.buildEvaluator(preferenceProvider);
                         var result      = evaluator.evaluate(triageInput);

                         if (result.decision() == TriageDecision.INCONCLUSIVE) {
                             return WorkerResult.of(TriageWorkerSupport.toResultMap(result), PlannedAction.of(
                                     "Investigation clearance — inconclusive evidence requires compliance review",
                                     AmlActionType.INVESTIGATION_CLEARANCE.actionType(),
                                     Map.of(
                                             "riskScore", String.valueOf(result.riskScore()),
                                             "reason", result.reason(),
                                             "factors", result.factors().stream()
                                                              .map(f -> f.name() + "=" + f.weight())
                                                              .collect(Collectors.joining(", ")))));
                         }
                         return TriageWorkerSupport.toWorkerResult(result);
                     })
                     .build();
    }

    private static TriageInput deserializeInput(ObjectMapper mapper, Map<String, Object> input) {
        var entity  = mapper.convertValue(input.get("entityResolution"), EntityResolutionResult.class);
        var pattern = mapper.convertValue(input.get("patternAnalysis"), PatternAnalysisResult.class);
        var osint   = mapper.convertValue(input.get("osintScreening"), OsintResult.class);
        CbrPathAdvice cbr = input.get("cbrPathAdvice") != null
                            ? mapper.convertValue(input.get("cbrPathAdvice"), CbrPathAdvice.class) : null;
        return new TriageInput(entity, pattern, osint, cbr);
    }
}
