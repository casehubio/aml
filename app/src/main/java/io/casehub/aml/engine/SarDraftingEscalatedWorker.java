package io.casehub.aml.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.aml.domain.EntityResolutionResult;
import io.casehub.aml.domain.OsintResult;
import io.casehub.aml.domain.PatternAnalysisResult;
import io.casehub.aml.domain.SeedNarrative;
import io.casehub.aml.domain.SuspiciousTransaction;
import io.casehub.aml.investigation.NarrativeContext;
import io.casehub.aml.investigation.SarNarrativeService;
import io.casehub.worker.api.Worker;
import io.casehub.worker.api.WorkerResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class SarDraftingEscalatedWorker {

    private SarDraftingEscalatedWorker() {}

    @SuppressWarnings("unchecked")
    public static Worker create(ObjectMapper objectMapper, SarNarrativeService sarNarrativeService) {
        return Worker.builder()
                     .name("sar-drafting-escalated-agent")
                     .capabilityName("sar-drafting-escalated")
                     .function((final Map<String, Object> input) -> {
                         var tx = objectMapper.convertValue(input.get("transaction"), SuspiciousTransaction.class);
                         var entity = objectMapper.convertValue(input.get("entityResolution"), EntityResolutionResult.class);
                         var pattern = objectMapper.convertValue(input.get("patternAnalysis"), PatternAnalysisResult.class);
                         var osint = objectMapper.convertValue(input.get("osintScreening"), OsintResult.class);
                         List<SeedNarrative> seeds = deserializeSeeds(objectMapper, input.get("similarSarNarratives"));

                         var context = new NarrativeContext(tx, entity, pattern, osint, seeds);
                         var result = sarNarrativeService.draft(context);

                         var output = new LinkedHashMap<String, Object>();
                         output.put("sarNarrative", result.narrative());
                         output.put("narrativeSeeded", result.seeded());
                         output.put("seedCount", result.seedCount());
                         output.put("adaptationMethod", result.adaptationMethod().name());
                         return WorkerResult.of(output);
                     })
                     .build();
    }

    @SuppressWarnings("unchecked")
    private static List<SeedNarrative> deserializeSeeds(ObjectMapper mapper, Object raw) {
        if (raw == null) return List.of();
        var list = (List<Map<String, Object>>) raw;
        return list.stream()
                   .map(m -> {
                       try {
                           return mapper.convertValue(m, SeedNarrative.class);
                       } catch (IllegalArgumentException e) {
                           return null;
                       }
                   })
                   .filter(Objects::nonNull)
                   .toList();
    }
}
