package io.casehub.aml.api.model;

import io.casehub.aml.compliance.AmlInclusionProof;
import io.casehub.aml.compliance.AmlProofStep;
import io.casehub.ledger.core.merkle.InclusionProof;


import java.util.UUID;

/**
 * REST response record for Merkle inclusion proofs.
 * Maps from {@link InclusionProof} to {@link AmlInclusionProof} with the entryId attached.
 */
public record InclusionProofResponse(
        UUID entryId,
        AmlInclusionProof proof
) {
    /**
     * Convert from ledger-library {@link InclusionProof} to REST response format.
     */
    public static InclusionProofResponse from(final InclusionProof ledgerProof) {
        final var siblings = ledgerProof.siblings().stream()
                .map(s -> new AmlProofStep(s.hash(), s.side().name()))
                .toList();
        final var amlProof = new AmlInclusionProof(
                ledgerProof.entryIndex(),
                ledgerProof.treeSize(),
                ledgerProof.leafHash(),
                siblings,
                ledgerProof.treeRoot()
        );
        return new InclusionProofResponse(ledgerProof.entryId(), amlProof);
    }
}
