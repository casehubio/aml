package io.casehub.aml.compliance;

import io.casehub.ledger.api.model.supplement.ComplianceSupplement;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AmlComplianceSupplementTest {

    @Test
    void triageDecision_allFieldsPopulated() {
        ComplianceSupplement s = AmlComplianceSupplement.triageDecision(
                "SAR_WARRANTED", 0.85, "entity-resolution→pattern-analysis→sar-drafting",
                "{\"flagReason\":\"HIGH_RISK_JURISDICTION\"}");
        assertEquals("GDPR Art.22(1) — automated decision-making transparency", s.planRef);
        assertEquals("AmlInvestigationTriageService (CBR-weighted rule-based triage)", s.algorithmRef);
        assertEquals(0.85, s.confidenceScore);
        assertNotNull(s.rationale);
        assertTrue(s.rationale.contains("SAR_WARRANTED"));
        assertTrue(s.rationale.contains("entity-resolution"));
        assertEquals("{\"flagReason\":\"HIGH_RISK_JURISDICTION\"}", s.decisionContext);
        assertTrue(s.humanOverrideAvailable);
        assertEquals("/api/investigations/{caseId}/contestation", s.contestationUri);
    }

    @Test
    void triageDecision_nullConfidence() {
        ComplianceSupplement s = AmlComplianceSupplement.triageDecision(
                "INVESTIGATION_CLEARED", null, "(direct-verdict)", null);
        assertNull(s.confidenceScore);
        assertNull(s.decisionContext);
        assertNotNull(s.algorithmRef);
        assertTrue(s.humanOverrideAvailable);
    }
}
