package io.casehub.aml.compliance;

import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CrossTenantErasureResultTest {

    @Test
    void construction_allFieldsAccessible() {
        UUID receiptId = UUID.randomUUID();
        var result = new CrossTenantErasureResult("ACCT-123", 3, 7, receiptId);

        assertEquals("ACCT-123", result.entityId());
        assertEquals(3, result.tenantsRequested());
        assertEquals(7, result.memoriesErased());
        assertEquals(receiptId, result.receiptEntryId());
    }

    @Test
    void request_construction() {
        var request = new CrossTenantErasureRequest(Set.of("tenant-a", "tenant-b"));

        assertEquals(2, request.tenantIds().size());
        assertTrue(request.tenantIds().contains("tenant-a"));
    }
}
