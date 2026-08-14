package io.casehub.aml.compliance;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class Art22DecisionRecordTest {

    @Test
    void construction_allFields() {
        UUID entryId = UUID.randomUUID();
        var record = new Art22DecisionRecord(
                entryId, "AlgorithmRef", 0.85, "rationale",
                true, "/api/contestation", true);
        assertEquals(entryId, record.entryId());
        assertEquals("AlgorithmRef", record.algorithmRef());
        assertEquals(0.85, record.confidenceScore());
        assertEquals("rationale", record.rationale());
        assertTrue(record.humanOverrideAvailable());
        assertEquals("/api/contestation", record.contestationUri());
        assertTrue(record.decisionContextPresent());
    }

    @Test
    void construction_nullableFields() {
        var record = new Art22DecisionRecord(
                UUID.randomUUID(), null, null, null,
                false, null, false);
        assertNull(record.algorithmRef());
        assertNull(record.confidenceScore());
        assertFalse(record.humanOverrideAvailable());
        assertFalse(record.decisionContextPresent());
    }
}
