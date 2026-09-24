package io.casehub.aml.service;

import io.casehub.aml.api.model.AuditTrailEntryResponse;
import io.casehub.aml.api.model.InclusionProofResponse;
import io.casehub.aml.provenance.AmlProvenanceService;
import io.casehub.aml.provenance.ProvDocument;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.core.merkle.InclusionProof;
import io.casehub.ledger.runtime.service.LedgerVerificationService;
import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestPath;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

import java.util.List;
import java.util.UUID;

@McpDomain(value = "aml/audit", app = "aml", basePath = "/api/investigations", summary = "AML audit trail — compliance evidence and decision history")
@ApplicationScoped
public class AmlAuditApi {

    @Inject LedgerEntryRepository ledgerEntryRepository;
    @Inject LedgerVerificationService verificationService;
    @Inject AmlProvenanceService provenanceService;

    @PlatformQuery("Get ledger audit trail for an investigation")
    @RestPath("/{caseId}/audit-trail")
    public List<AuditTrailEntryResponse> getAuditTrail(@PathParam UUID caseId) {
        return ledgerEntryRepository.findBySubjectId(caseId, TenancyConstants.DEFAULT_TENANT_ID)
                .stream()
                .map(AuditTrailEntryResponse::from)
                .toList();
    }

    @PlatformQuery("Get Merkle inclusion proof for a ledger entry")
    @RestPath("/{caseId}/audit-trail/{entryId}/proof")
    public InclusionProofResponse getInclusionProof(@PathParam UUID caseId,
                                                     @PathParam UUID entryId) {
        boolean belongsToCase = ledgerEntryRepository
                .findBySubjectId(caseId, TenancyConstants.DEFAULT_TENANT_ID)
                .stream()
                .anyMatch(e -> e.id.equals(entryId));
        if (!belongsToCase) {
            throw new NotFoundException("Entry not found for case: " + caseId);
        }
        InclusionProof proof = verificationService.inclusionProof(
                entryId, TenancyConstants.DEFAULT_TENANT_ID);
        return InclusionProofResponse.from(proof);
    }

    @PlatformQuery("Get W3C PROV-DM provenance for an investigation")
    @RestPath("/{caseId}/provenance")
    public ProvDocument getProvenance(@PathParam UUID caseId) {
        return provenanceService.buildProvenance(caseId)
                .orElseThrow(() -> new NotFoundException("No provenance for case: " + caseId));
    }
}
