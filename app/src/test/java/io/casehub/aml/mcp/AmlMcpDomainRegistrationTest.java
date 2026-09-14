package io.casehub.aml.mcp;

import io.casehub.platform.mcp.DomainModelRegistry;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class AmlMcpDomainRegistrationTest {

    @Inject
    DomainModelRegistry registry;

    @Test
    void amlInvestigationsDomainRegistered() {
        var domain = registry.getDomain("aml/investigations");
        assertTrue(domain.isPresent(), "aml/investigations domain should be registered");
        assertFalse(domain.get().operations().isEmpty(), "should have operations");
    }

    @Test
    void amlComplianceDomainRegistered() {
        var domain = registry.getDomain("aml/compliance");
        assertTrue(domain.isPresent(), "aml/compliance domain should be registered");
    }

    @Test
    void amlAuditDomainRegistered() {
        var domain = registry.getDomain("aml/audit");
        assertTrue(domain.isPresent(), "aml/audit domain should be registered");
    }

    @Test
    void investigationOperationsPresent() {
        var domain = registry.getDomain("aml/investigations").orElseThrow();
        assertTrue(domain.operations().stream().anyMatch(op -> op.name().equals("getInvestigation")));
        assertTrue(domain.operations().stream().anyMatch(op -> op.name().equals("listStalled")));
    }
}
