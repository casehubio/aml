package io.casehub.aml.api.mcp;

import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformQuery;

import java.util.List;
import java.util.UUID;

@McpDomain(value = "aml/audit", app = "aml")
public interface AmlAuditApi {

    @PlatformQuery("Full causal audit chain with Merkle verification")
    List<AuditTrailEntry> getAuditTrail(@PathParam("caseId") UUID caseId);
}
