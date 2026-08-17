package io.casehub.aml.compliance;

import io.casehub.blocks.routing.RequirementStatus;

public record GdprErasureRequirement(
        String id,
        String citation,
        String mechanism,
        RequirementStatus status,
        boolean tokenisationEnabled,
        boolean erasureReceiptEnabled,
        long erasureReceiptCount,
        String erasureEndpoint,
        String retentionCitation,
        String retentionAdrRef) {

    public static final String REQUIREMENT_ID = "GDPR-ART17-ERASURE";
    public static final String CITATION =
            "GDPR Art. 17 / FATF privacy obligation — PII erasure preserving audit structure";
    public static final String MECHANISM =
            "LedgerErasureService pseudonymizes actorId in ledger_entry rows via ActorIdentity token. " +
            "Audit entries remain intact; actor identity is replaced with an opaque token. " +
            "Tamper-evident ErasureReceiptLedgerEntry records each erasure in the Merkle chain.";
    public static final String ERASURE_ENDPOINT = "POST /api/actors/{actorId}/erasure";
    public static final String RETENTION_CITATION =
            "GDPR Art.17(3)(b), BSA 31 CFR 1020.320(d), 4AMLD Art.40, FATF Rec.11";
}
