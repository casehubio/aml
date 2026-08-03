package io.casehub.aml.cbr;

import io.casehub.aml.domain.SeedNarrative;
import io.casehub.ledger.runtime.privacy.ContentSanitiser;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class SarNarrativeSeeder {

    private final ContentSanitiser sanitiser;

    public SarNarrativeSeeder(ContentSanitiser sanitiser) {
        this.sanitiser = sanitiser;
    }

    @SuppressWarnings("unchecked")
    public List<SeedNarrative> extract(List<Map<String, Object>> experiences) {
        if (experiences == null || experiences.isEmpty()) {
            return List.of();
        }
        return experiences.stream()
                .filter(e -> "SAR_WARRANTED".equals(e.get("outcome")))
                .filter(e -> extractFeatureString(e, "sar_narrative") != null)
                .filter(e -> e.get("similarityScore") instanceof Number n && n.doubleValue() > 0)
                .map(e -> {
                    String raw = extractFeatureString(e, "sar_narrative");
                    double score = ((Number) e.get("similarityScore")).doubleValue();
                    String flagReason = extractFeatureString(e, "flag_reason");
                    String entityType = extractFeatureString(e, "entity_type");
                    return new SeedNarrative(sanitiser.sanitise(raw), score, flagReason, entityType);
                })
                .sorted(Comparator.comparingDouble(SeedNarrative::similarityScore).reversed())
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static String extractFeatureString(Map<String, Object> experience, String featureName) {
        var features = (Map<String, Object>) experience.get("features");
        if (features == null) return null;
        Object val = features.get(featureName);
        if (val instanceof String s) return s;
        if (val instanceof Map<?, ?> m) return m.get("value") instanceof String s ? s : null;
        return null;
    }
}
