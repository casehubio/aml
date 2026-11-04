package io.casehub.aml.domain;

import java.util.Objects;

public record SarOutcome(
        SarVerdict verdict,
        String reason,
        double investigationAccuracyScore) {

    public SarOutcome {
        Objects.requireNonNull(verdict, "verdict must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        if (!Double.isFinite(investigationAccuracyScore)
                || investigationAccuracyScore < 0.0 || investigationAccuracyScore > 1.0) {
            throw new IllegalArgumentException(
                    "investigationAccuracyScore must be in [0.0, 1.0], got: " + investigationAccuracyScore);
        }
    }
}
