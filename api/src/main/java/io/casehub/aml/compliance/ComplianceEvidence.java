package io.casehub.aml.compliance;

import io.casehub.blocks.routing.TrustRoutingRequirement;

import java.time.Instant;
import java.util.UUID;

public record ComplianceEvidence(
        UUID caseId,
        Instant generatedAt,
        AuditChainRequirement auditChain,
        SlaRequirement sla,
        TrustRoutingRequirement trustRouting,
        GdprErasureRequirement gdprErasure,
        Art22DecisionRecordRequirement art22DecisionRecord,
        String signature
) {}
