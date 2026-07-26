package io.casehub.aml.domain;

public record SeedNarrative(
        String narrative,
        double similarityScore,
        String flagReason,
        String entityType
) {}
