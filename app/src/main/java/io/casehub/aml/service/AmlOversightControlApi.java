package io.casehub.aml.service;

import io.casehub.api.engine.CaseHubRuntime;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.RestPath;
import io.casehub.platform.api.mcp.RestStatus;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.util.Map;
import java.util.UUID;

@McpDomain(value = "aml/oversight-control", app = "aml", basePath = "/api")
@ApplicationScoped
public class AmlOversightControlApi {

    @Inject CaseHubRuntime caseHubRuntime;

    @PlatformMutation("Suspend an active investigation")
    @RestPath("/layer9/investigations/{caseId}/suspend")
    @RolesAllowed({"aml-compliance", "aml-mlro"})
    @RestStatus(204)
    public void suspendInvestigation(@PathParam UUID caseId) {
        try {
            caseHubRuntime.suspendCase(caseId);
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("not found")) {
                throw new NotFoundException(e.getMessage());
            }
            throw new WebApplicationException(Response.status(409)
                    .entity(Map.of("error", e.getMessage())).build());
        }
    }

    @PlatformMutation("Resume a suspended investigation")
    @RestPath("/layer9/investigations/{caseId}/resume")
    @RolesAllowed({"aml-compliance", "aml-mlro"})
    @RestStatus(204)
    public void resumeInvestigation(@PathParam UUID caseId) {
        try {
            caseHubRuntime.resumeCase(caseId);
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("not found")) {
                throw new NotFoundException(e.getMessage());
            }
            throw new WebApplicationException(Response.status(409)
                    .entity(Map.of("error", e.getMessage())).build());
        }
    }
}
