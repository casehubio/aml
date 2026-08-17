package io.casehub.aml.compliance;

import io.casehub.blocks.routing.RequirementStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GdprErasureRequirementTest {

    @Test
    void construction_includesRetentionExemptionFields() {
        var req = new GdprErasureRequirement(
                GdprErasureRequirement.REQUIREMENT_ID,
                GdprErasureRequirement.CITATION,
                GdprErasureRequirement.MECHANISM,
                RequirementStatus.CLOSED,
                true, true, 5L,
                GdprErasureRequirement.ERASURE_ENDPOINT,
                GdprErasureRequirement.RETENTION_CITATION,
                "ADR-0004");

        assertEquals(GdprErasureRequirement.RETENTION_CITATION, req.retentionCitation());
        assertEquals("ADR-0004", req.retentionAdrRef());
        assertTrue(req.retentionCitation().contains("Art.17(3)(b)"));
    }

    @Test
    void retentionCitation_constant_containsAllRegulatoryCitations() {
        String citation = GdprErasureRequirement.RETENTION_CITATION;
        assertTrue(citation.contains("Art.17(3)(b)"));
        assertTrue(citation.contains("BSA 31 CFR 1020.320(d)"));
        assertTrue(citation.contains("4AMLD Art.40"));
        assertTrue(citation.contains("FATF Rec.11"));
    }
}
