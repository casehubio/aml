package io.casehub.aml.domain;

public record TriageInput(
        EntityResolutionResult entityResolution,
        PatternAnalysisResult patternAnalysis,
        OsintResult osintScreening,
        CbrPathAdvice cbrPathAdvice,
        SeniorAnalystReview seniorAnalystReview,
        RejectionContext rejectionContext) {

    public TriageInput(
            EntityResolutionResult entityResolution,
            PatternAnalysisResult patternAnalysis,
            OsintResult osintScreening,
            CbrPathAdvice cbrPathAdvice) {
        this(entityResolution, patternAnalysis, osintScreening, cbrPathAdvice, null, null);
    }
}
