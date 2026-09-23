package io.casehub.aml.api.mcp;

import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformQuery;

import java.util.List;
import java.util.UUID;

@McpDomain(value = "aml/investigations", app = "aml")
public interface AmlInvestigationApi {

    @PlatformQuery("Investigation status — outcome, specialist findings, gate decisions")
    InvestigationDetail getInvestigation(@PathParam("caseId") UUID caseId);

    @PlatformQuery("Investigations with workers past stall threshold")
    List<StalledInvestigation> listStalled();
}
