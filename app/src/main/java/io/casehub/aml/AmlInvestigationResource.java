package io.casehub.aml;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import io.casehub.aml.domain.AmlInvestigationResult;
import io.casehub.aml.domain.SuspiciousTransaction;

@Path("/api/investigations")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AmlInvestigationResource {

    @Inject
    AmlInvestigationApplicationService investigationService;

    @POST
    public AmlInvestigationResult investigate(SuspiciousTransaction transaction) {
        return investigationService.investigate(transaction);
    }
}
