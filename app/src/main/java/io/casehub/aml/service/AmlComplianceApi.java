package io.casehub.aml.service;

import io.casehub.aml.compliance.AmlComplianceEvidenceService;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestPath;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

import java.util.UUID;

@McpDomain(value = "aml/compliance", basePath = "/api/investigations")
@ApplicationScoped
public class AmlComplianceApi {

    @Inject AmlComplianceEvidenceService evidenceService;

    @PlatformQuery("Get compliance evidence for an investigation")
    @RestPath("/{caseId}/compliance-evidence")
    public Object getComplianceEvidence(@PathParam UUID caseId) {
        return evidenceService.findEvidence(caseId)
                .orElseThrow(() -> new NotFoundException("No compliance evidence for case: " + caseId));
    }
}
