package io.casehub.aml.domain;

import java.util.Objects;

public record SeniorAnalystReview(
        double riskAdjustment,
        String finding,
        String recommendedAction) {

    public SeniorAnalystReview {
        if (riskAdjustment < -1.0 || riskAdjustment > 1.0) {
            throw new IllegalArgumentException(
                    "riskAdjustment must be in [-1.0, 1.0], got: " + riskAdjustment);
        }
        Objects.requireNonNull(finding);
        Objects.requireNonNull(recommendedAction);
    }
}
