package io.casehub.aml.api.mcp;

public record SarPipelineStatus(
        int pendingReviews,
        int pendingGates,
        long averageReviewAgeMs,
        int slaBreaches
) {}
