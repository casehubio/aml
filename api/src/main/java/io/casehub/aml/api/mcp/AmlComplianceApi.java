package io.casehub.aml.api.mcp;

import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformQuery;

@McpDomain(value = "aml/compliance", app = "aml")
public interface AmlComplianceApi {

    @PlatformQuery("SAR pipeline — pending reviews, queue depth, SLA health")
    SarPipelineStatus getSarPipeline();
}
