package io.casehub.aml.api.mcp;

import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformQuery;

@McpDomain(value = "aml/compliance", app = "aml", summary = "AML regulatory compliance — evidence gathering and reporting")
public interface AmlComplianceApi {

    @PlatformQuery("SAR pipeline — pending reviews, queue depth, SLA health")
    SarPipelineStatus getSarPipeline();
}
