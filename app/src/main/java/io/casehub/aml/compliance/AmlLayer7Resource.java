package io.casehub.aml.compliance;

import io.casehub.ledger.api.model.ErasureReason;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.UUID;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.QueryParam;

/**
 * Layer 7: compliance evidence endpoint.
 *
 * <p>GET /api/investigations/{caseId}/compliance-evidence — maps FinCEN/FATF
 * requirements to evidence artifacts for a completed investigation.
 *
 * <p>Uses a fully-specified class-level @Path to avoid JAX-RS dispatch ambiguity
 * with other /api/investigations/* resources.
 */
@ApplicationScoped
@Path("/api/investigations/{caseId}/compliance-evidence")
@Produces(MediaType.APPLICATION_JSON)
public class AmlLayer7Resource {

    @Inject
    AmlComplianceEvidenceService evidenceService;

    @GET
    public Response getComplianceEvidence(@PathParam("caseId") UUID caseId) {
        return evidenceService.findEvidence(caseId)
            .map(e -> Response.ok(e).build())
            .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }
}

/**
 * Layer 7: GDPR erasure endpoint.
 *
 * <p>POST /api/actors/{actorId}/erasure — pseudonymizes an actor's identity in
 * ledger entries, preserving audit structure while satisfying GDPR Art. 17.
 */
@ApplicationScoped
@Path("/api/actors/{actorId}/erasure")
@Produces(MediaType.APPLICATION_JSON)
class AmlGdprErasureResource {

    @Inject
    AmlErasureService erasureService;

    @POST
    public ActorErasureResult eraseActor(@PathParam("actorId") String actorId) {
        return erasureService.erase(actorId, ErasureReason.GDPR_ART_17_REQUEST);
    }
}

/**
 * Layer 7: Entity memory erasure endpoint.
 *
 * <p>POST /api/entities/{entityId}/erasure — erases all CaseMemoryStore records
 * for an entity, writes audit ledger entry with receipt.
 */
@ApplicationScoped
@Path("/api/entities/{entityId}/erasure")
@Produces(MediaType.APPLICATION_JSON)
class AmlEntityErasureResource {

    @Inject
    AmlErasureService erasureService;

    @POST
    public EntityErasureResult eraseEntity(
            @PathParam("entityId") String entityId,
            @QueryParam("tenantId") String tenantId) {
        if (tenantId != null) {
            return erasureService.eraseEntity(entityId, tenantId, ErasureReason.GDPR_ART_17_REQUEST);
        }
        return erasureService.eraseEntity(entityId, ErasureReason.GDPR_ART_17_REQUEST);
    }
}

@ApplicationScoped
@Path("/api/entities/{entityId}/erasure/cross-tenant")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class AmlCrossTenantErasureResource {

    @Inject
    AmlErasureService erasureService;

    @POST
    public CrossTenantErasureResult eraseEntityAcrossTenants(
            @PathParam("entityId") String entityId,
            CrossTenantErasureRequest request) {
        return erasureService.eraseEntityAcrossTenants(
                entityId, request.tenantIds(), ErasureReason.GDPR_ART_17_REQUEST);
    }
}
