package io.casehub.aml.compliance;

import io.casehub.agency.governance.routing.RequirementStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SlaRequirement(
    String id,
    String citation,
    String mechanism,
    RequirementStatus status,
    UUID workItemId,
    Instant claimDeadline,
    Instant completedAt,
    boolean slaMet,
    List<String> candidateGroups,
    String escalationPolicy
) {
    public static final String REQUIREMENT_ID = "FINCEN-SAR-30DAY-SLA";
    public static final String CITATION =
        "31 CFR § 1020.320(b)(3) — SAR human sign-off with 30-day filing deadline";
    public static final String MECHANISM =
        "Layer 2: casehub-work WorkItem with claimDeadline + candidateGroups=compliance-officers. " +
        "Layer 5: engine auto-escalation to senior-compliance-officers on deadline breach.";
    public static final String ESCALATION_POLICY =
        "senior-compliance-officers after claimDeadline breach";
}
