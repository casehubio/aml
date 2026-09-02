package io.casehub.aml.api.model;

import java.util.List;

public record InterventionMetrics(
    int escalationCount,
    int manualOverrideCount,
    int declineRoutingCount,
    int gateRejectionCount,
    double averageResponseTimeSeconds,
    List<RecentIntervention> recentInterventions
) {
    public record RecentIntervention(
        String type,
        String caseId,
        String reason,
        String actor,
        String occurredAt
    ) {}
}
