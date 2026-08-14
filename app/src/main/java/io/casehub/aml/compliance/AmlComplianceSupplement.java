package io.casehub.aml.compliance;

import io.casehub.ledger.api.model.supplement.ComplianceSupplement;
import io.casehub.ledger.runtime.model.supplement.JpaComplianceSupplement;

public final class AmlComplianceSupplement {

    private AmlComplianceSupplement() {}

    public static ComplianceSupplement triageDecision(
            String outcome, Double confidence, String investigationPath,
            String sanitisedDecisionContext) {
        ComplianceSupplement s = new JpaComplianceSupplement();
        s.planRef = "GDPR Art.22(1) — automated decision-making transparency";
        s.algorithmRef = "AmlInvestigationTriageService (CBR-weighted rule-based triage)";
        s.confidenceScore = confidence;
        s.rationale = "Triage outcome: " + outcome + ". Path: " + investigationPath;
        s.decisionContext = sanitisedDecisionContext;
        s.humanOverrideAvailable = true;
        s.contestationUri = "/api/investigations/{caseId}/contestation";
        return s;
    }
}
