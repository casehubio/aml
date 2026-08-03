package io.casehub.aml.engine;

import io.casehub.aml.api.model.AuditTrailEntryResponse;
import io.casehub.aml.api.model.InclusionProofResponse;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.runtime.service.LedgerVerificationService;
import io.casehub.ledger.runtime.service.model.InclusionProof;
import io.casehub.platform.api.identity.TenancyConstants;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.UUID;

/**
 * Layer 9: REST endpoints for querying the ledger audit trail for an investigation.
 *
 * <p>Consumed by the {@code <aml-audit-trail>} panel (Task 8).
 *
 * <p>Two endpoints:
 * <ol>
 * <li>{@code GET /api/investigations/{caseId}/audit-trail} — all ledger entries for the case</li>
 * <li>{@code GET /api/investigations/{caseId}/audit-trail/{entryId}/proof} — Merkle inclusion proof</li>
 * </ol>
 */
@Path("/api/investigations/{caseId}/audit-trail")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class AmlAuditTrailResource {

    @Inject
    LedgerEntryRepository ledgerEntryRepository;

    @Inject
    LedgerVerificationService verificationService;

    /**
     * Return all ledger entries for a given caseId, ordered by sequence number.
     *
     * <p>The caseId is used directly as the subjectId — no namespace prefix is applied,
     * per {@link io.casehub.aml.ledger.AmlLedgerService#writeCaseOpened}.
     *
     * <p>Returns an empty list if no entries exist for the caseId (not 404).
     */
    @GET
    public List<AuditTrailEntryResponse> getAuditTrail(@PathParam("caseId") UUID caseId) {
        return ledgerEntryRepository.findBySubjectId(caseId, TenancyConstants.DEFAULT_TENANT_ID)
                .stream()
                .map(AuditTrailEntryResponse::from)
                .toList();
    }

    /**
     * Return the Merkle inclusion proof for a specific ledger entry.
     *
     * <p>Verifies that the entry belongs to the specified case before returning the proof.
     * Returns 404 if the entry doesn't exist or doesn't belong to this case.
     */
    @GET
    @Path("/{entryId}/proof")
    public Response getInclusionProof(
            @PathParam("caseId") UUID caseId,
            @PathParam("entryId") UUID entryId) {
        boolean belongsToCase = ledgerEntryRepository
                .findBySubjectId(caseId, TenancyConstants.DEFAULT_TENANT_ID)
                .stream()
                .anyMatch(e -> e.id.equals(entryId));
        if (!belongsToCase) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        try {
            final InclusionProof proof = verificationService.inclusionProof(
                    entryId,
                    TenancyConstants.DEFAULT_TENANT_ID
            );
            return Response.ok(InclusionProofResponse.from(proof)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(e.getMessage())
                    .build();
        }
    }
}
