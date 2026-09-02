package io.casehub.aml.api.model;

import java.time.Instant;

public record AdaptiveDecision(
    String trigger,
    String condition,
    boolean fired,
    Instant timestamp
) {}
