package io.casehub.aml.supervisor;

import java.util.List;
import java.util.Objects;

public record SupervisorDecision(
        List<String> selectedBindings,
        List<String> suppressedBindings,
        String rationale,
        boolean earlyTermination) {

    public SupervisorDecision {
        Objects.requireNonNull(selectedBindings, "selectedBindings required");
        Objects.requireNonNull(suppressedBindings, "suppressedBindings required");
        Objects.requireNonNull(rationale, "rationale required");
        if (selectedBindings.isEmpty()) {
            throw new IllegalArgumentException("selectedBindings must not be empty");
        }
        selectedBindings = List.copyOf(selectedBindings);
        suppressedBindings = List.copyOf(suppressedBindings);
    }
}
