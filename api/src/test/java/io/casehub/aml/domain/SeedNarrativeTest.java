package io.casehub.aml.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SeedNarrativeTest {

    @Test
    void recordFieldsAccessible() {
        var sn = new SeedNarrative("narrative text", 0.85, "STRUCTURING", "SHELL_COMPANY");
        assertEquals("narrative text", sn.narrative());
        assertEquals(0.85, sn.similarityScore(), 0.001);
        assertEquals("STRUCTURING", sn.flagReason());
        assertEquals("SHELL_COMPANY", sn.entityType());
    }

    @Test
    void nullableFieldsPermitted() {
        var sn = new SeedNarrative("text", 0.5, null, null);
        assertNull(sn.flagReason());
        assertNull(sn.entityType());
    }
}
