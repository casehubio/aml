package io.casehub.aml.investigation;

import io.casehub.aml.domain.*;
import java.util.List;
import java.util.Objects;

public record NarrativeContext(
        SuspiciousTransaction transaction,
        EntityResolutionResult entity,
        PatternAnalysisResult pattern,
        OsintResult osint,
        List<SeedNarrative> seeds
) {
    public NarrativeContext {
        Objects.requireNonNull(transaction, "transaction is required for SAR narrative drafting");
        if (seeds == null) seeds = List.of();
    }
}
