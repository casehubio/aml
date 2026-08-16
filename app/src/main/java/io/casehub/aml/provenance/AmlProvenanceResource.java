package io.casehub.aml.provenance;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.UUID;

@ApplicationScoped
@Path("/api/investigations/{caseId}/provenance")
@Produces(MediaType.APPLICATION_JSON)
public class AmlProvenanceResource {

    @Inject
    AmlProvenanceService provenanceService;

    @GET
    public Response getProvenance(@PathParam("caseId") UUID caseId) {
        return provenanceService.buildProvenance(caseId)
            .map(doc -> Response.ok(doc).build())
            .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }
}
