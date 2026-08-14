package io.casehub.aml.supervisor;

import java.util.UUID;

public record PendingSupervisorDecision(
        SupervisorDecision decision,
        int eligibleCount,
        boolean degraded,
        UUID caseId,
        String tenancyId) {
}
