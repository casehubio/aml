package io.casehub.aml.domain;

public record CbrPathAdvice(
        int caseCount,
        double avgSimilarity,
        double confidence,
        String predominantOutcome,
        Double predominantOutcomeFrequency,
        boolean error,
        boolean active) {}
