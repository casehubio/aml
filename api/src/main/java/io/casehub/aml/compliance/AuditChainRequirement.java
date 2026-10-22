package io.casehub.aml.compliance;

import io.casehub.agency.routing.RequirementStatus;

import java.util.List;

public record AuditChainRequirement(
    String id,
    String citation,
    String mechanism,
    RequirementStatus status,
    String treeRoot,
    boolean chainVerified,
    List<LedgerEventRecord> events
) {
    public static final String REQUIREMENT_ID = "FINCEN-31CFR1020.320-AUDIT-CHAIN";
    public static final String CITATION =
        "31 CFR § 1020.320(a) / FATF R.16 — Auditable evidence chain with tamper-evident record";
    public static final String MECHANISM =
        "Layers 3+4: qhorus COMMAND/DONE/DECLINE per specialist + Merkle-chained LedgerEntry " +
        "(causedByEntryId links COMPLIANCE_REVIEW_OPENED to CASE_OPENED). " +
        "SCOPE: covers AML domain lifecycle events only. " +
        "Specialist dispatch audit lives in the qhorus ledger chain (subjectId = caseId).";
}
