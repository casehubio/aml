package io.casehub.aml.investigation;

public record NarrativeResult(
        String narrative,
        boolean seeded,
        int seedCount,
        AdaptationMethod adaptationMethod
) {}
