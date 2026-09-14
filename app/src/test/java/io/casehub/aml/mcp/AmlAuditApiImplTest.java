package io.casehub.aml.mcp;

import io.casehub.aml.api.mcp.AuditTrailEntry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AmlAuditApiImplTest {

    @Test
    void auditTrailEntry_isRecord() {
        UUID id = UUID.randomUUID();
        UUID causedBy = UUID.randomUUID();
        AuditTrailEntry entry = new AuditTrailEntry(
                id, "AmlInvestigationLedgerEntry", "agent-1", "SYSTEM",
                Instant.now(), 1L, causedBy, "sha256:abc");
        assertEquals(id, entry.id());
        assertEquals("AmlInvestigationLedgerEntry", entry.dtype());
        assertEquals(causedBy, entry.causedByEntryId());
    }
}
