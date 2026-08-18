package io.casehub.aml.cbr;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.aml.domain.CbrPathAdvice;
import io.casehub.aml.domain.EntityResolutionResult;
import io.casehub.aml.domain.OsintResult;
import io.casehub.aml.domain.PatternAnalysisResult;
import io.casehub.aml.domain.RejectionContext;
import io.casehub.aml.domain.SeniorAnalystReview;
import io.casehub.aml.domain.TriageInput;
import io.casehub.platform.api.preferences.PreferenceProvider;
import io.casehub.worker.api.Worker;

import java.util.Map;

public final class PostRejectionTriageWorker {

    private PostRejectionTriageWorker() {}

    public static Worker create(ObjectMapper objectMapper, PreferenceProvider preferenceProvider) {
        return Worker.builder()
                     .name("post-rejection-triage-agent")
                     .capabilityName("post-rejection-triage")
                     .function((Map<String, Object> input) -> {
                         var entity  = objectMapper.convertValue(input.get("entityResolution"), EntityResolutionResult.class);
                         var pattern = objectMapper.convertValue(input.get("patternAnalysis"), PatternAnalysisResult.class);
                         var osint   = objectMapper.convertValue(input.get("osintScreening"), OsintResult.class);
                         CbrPathAdvice cbr = input.get("cbrPathAdvice") != null
                                             ? objectMapper.convertValue(input.get("cbrPathAdvice"), CbrPathAdvice.class) : null;
                         SeniorAnalystReview review = input.get("seniorAnalystReview") != null
                                                      ? objectMapper.convertValue(input.get("seniorAnalystReview"), SeniorAnalystReview.class) : null;
                         RejectionContext rejection = input.get("rejectionContext") != null
                                                      ? objectMapper.convertValue(input.get("rejectionContext"), RejectionContext.class) : null;

                         var triageInput = new TriageInput(entity, pattern, osint, cbr, review, rejection);
                         var evaluator   = TriageWorkerSupport.buildEvaluator(preferenceProvider);
                         var result      = evaluator.evaluate(triageInput);
                         return TriageWorkerSupport.toWorkerResult(result);
                     })
                     .build();
    }
}
