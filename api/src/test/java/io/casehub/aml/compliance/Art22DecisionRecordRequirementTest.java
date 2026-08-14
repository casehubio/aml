package io.casehub.aml.compliance;

import io.casehub.blocks.routing.RequirementStatus;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class Art22DecisionRecordRequirementTest {

    @Test
    void constants() {
        assertEquals("GDPR-ART22-DECISION-RECORD", Art22DecisionRecordRequirement.REQUIREMENT_ID);
        assertNotNull(Art22DecisionRecordRequirement.CITATION);
        assertNotNull(Art22DecisionRecordRequirement.MECHANISM);
    }

    @Test
    void construction_closed() {
        var decision = new Art22DecisionRecord(
                UUID.randomUUID(), "alg", 0.9, "rationale", true, "/contest", true);
        var req = new Art22DecisionRecordRequirement(
                Art22DecisionRecordRequirement.REQUIREMENT_ID,
                Art22DecisionRecordRequirement.CITATION,
                Art22DecisionRecordRequirement.MECHANISM,
                RequirementStatus.CLOSED, List.of(decision));
        assertEquals(RequirementStatus.CLOSED, req.status());
        assertEquals(1, req.decisions().size());
    }

    @Test
    void construction_gap() {
        var req = new Art22DecisionRecordRequirement(
                Art22DecisionRecordRequirement.REQUIREMENT_ID,
                Art22DecisionRecordRequirement.CITATION,
                Art22DecisionRecordRequirement.MECHANISM,
                RequirementStatus.GAP, List.of());
        assertEquals(RequirementStatus.GAP, req.status());
        assertTrue(req.decisions().isEmpty());
    }
}
