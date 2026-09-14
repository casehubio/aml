package io.casehub.aml.mcp;

import io.casehub.aml.api.mcp.AmlAuditApi;
import io.casehub.aml.api.mcp.AuditTrailEntry;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.platform.api.identity.TenancyConstants;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class AmlAuditApiImpl implements AmlAuditApi {

    @Inject
    LedgerEntryRepository ledgerEntryRepository;

    @Override
    public List<AuditTrailEntry> getAuditTrail(UUID caseId) {
        return ledgerEntryRepository.findBySubjectId(caseId, TenancyConstants.DEFAULT_TENANT_ID)
                .stream()
                .map(e -> new AuditTrailEntry(
                        e.id,
                        e.getClass().getSimpleName(),
                        e.actorId,
                        e.actorType != null ? e.actorType.name() : null,
                        e.timestamp,
                        e.sequenceNumber,
                        e.causedByEntryId,
                        e.digest))
                .toList();
    }
}
