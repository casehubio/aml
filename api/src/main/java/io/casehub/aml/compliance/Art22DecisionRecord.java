package io.casehub.aml.compliance;

import java.util.UUID;

public record Art22DecisionRecord(
        UUID entryId,
        String algorithmRef,
        Double confidenceScore,
        String rationale,
        boolean humanOverrideAvailable,
        String contestationUri,
        boolean decisionContextPresent) {}
