package io.casehub.aml.api.model;

import java.util.List;

public record InvestigationRoutingResponse(
    List<RoutingDecision> decisions
) {
    public record RoutingDecision(
        String capabilityTag,
        String selectedWorker,
        Double trustScoreAtRouting,
        List<AlternativeCandidate> alternativesConsidered,
        String rationale
    ) {}

    public record AlternativeCandidate(
        String workerId,
        Double score
    ) {}
}
