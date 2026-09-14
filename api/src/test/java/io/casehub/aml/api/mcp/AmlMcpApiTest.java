package io.casehub.aml.api.mcp;

import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformQuery;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AmlMcpApiTest {

    @Test
    void investigationApiHasMcpDomain() {
        McpDomain domain = AmlInvestigationApi.class.getAnnotation(McpDomain.class);
        assertNotNull(domain);
        assertEquals("aml/investigations", domain.value());
    }

    @Test
    void complianceApiHasMcpDomain() {
        McpDomain domain = AmlComplianceApi.class.getAnnotation(McpDomain.class);
        assertNotNull(domain);
        assertEquals("aml/compliance", domain.value());
    }

    @Test
    void auditApiHasMcpDomain() {
        McpDomain domain = AmlAuditApi.class.getAnnotation(McpDomain.class);
        assertNotNull(domain);
        assertEquals("aml/audit", domain.value());
    }

    @Test
    void investigationApiMethodsHavePlatformQuery() throws NoSuchMethodException {
        assertNotNull(AmlInvestigationApi.class.getMethod("getInvestigation", UUID.class)
                .getAnnotation(PlatformQuery.class));
        assertNotNull(AmlInvestigationApi.class.getMethod("listStalled")
                .getAnnotation(PlatformQuery.class));
    }

    @Test
    void complianceApiMethodsHavePlatformQuery() throws NoSuchMethodException {
        assertNotNull(AmlComplianceApi.class.getMethod("getSarPipeline")
                .getAnnotation(PlatformQuery.class));
    }

    @Test
    void auditApiMethodsHavePlatformQuery() throws NoSuchMethodException {
        assertNotNull(AmlAuditApi.class.getMethod("getAuditTrail", UUID.class)
                .getAnnotation(PlatformQuery.class));
    }
}
