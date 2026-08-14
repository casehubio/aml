package io.casehub.aml.compliance;

import io.casehub.blocks.routing.RequirementStatus;
import java.util.List;

public record Art22DecisionRecordRequirement(
        String id,
        String citation,
        String mechanism,
        RequirementStatus status,
        List<Art22DecisionRecord> decisions) {

    public static final String REQUIREMENT_ID = "GDPR-ART22-DECISION-RECORD";
    public static final String CITATION =
            "GDPR Art.22 — automated decision-making transparency and contestation rights";
    public static final String MECHANISM =
            "ComplianceSupplement attached to AmlCaseProfileLedgerEntry at case completion. " +
            "Records algorithm reference, confidence score, sanitised decision context, " +
            "and contestation URI. Human override available via compliance officer review gate.";
}
