package io.casehub.aml.provenance;

import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.runtime.service.LedgerVerificationService;
import io.casehub.ledger.runtime.service.model.InclusionProof;
import io.casehub.platform.api.identity.TenancyConstants;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class AmlProvenanceService {

    @Inject
    LedgerEntryRepository ledgerRepo;

    @Inject
    LedgerVerificationService verificationService;

    private final ProvDmMapper mapper = new ProvDmMapper();

    public Optional<ProvDocument> buildProvenance(UUID caseId) {
        List<LedgerEntry> entries = ledgerRepo.findBySubjectId(
            caseId, TenancyConstants.DEFAULT_TENANT_ID);
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        Map<UUID, InclusionProof> proofs = new LinkedHashMap<>();
        for (LedgerEntry entry : entries) {
            try {
                InclusionProof proof = verificationService.inclusionProof(
                    entry.id, TenancyConstants.DEFAULT_TENANT_ID);
                proofs.put(entry.id, proof);
            } catch (Exception ignored) {
            }
        }
        return Optional.of(mapper.map(entries, proofs));
    }
}
