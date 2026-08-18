package io.casehub.aml.triage;

import io.casehub.aml.domain.RiskFactor;
import io.casehub.aml.domain.TriageInput;

import java.util.ArrayList;
import java.util.List;

public final class RiskScorer {

    public record ScoringResult(double score, List<RiskFactor> factors) {}

    public ScoringResult score(TriageInput input) {
        var    factors = new ArrayList<RiskFactor>();
        double total   = 0.0;

        double entityRisk = Math.max(0.0, Math.min(1.0, input.entityResolution().riskScore()));
        total += entityRisk * 0.35;
        factors.add(new RiskFactor("entity-risk-score", 0.35,
                                   "riskScore=" + entityRisk));

        double structuring = input.patternAnalysis().structuringDetected() ? 1.0 : 0.0;
        total += structuring * 0.25;
        if (structuring > 0) {
            factors.add(new RiskFactor("structuring-detected", 0.25,
                                       input.patternAnalysis().description()));
        }

        boolean isPep   = "PEP".equals(input.entityResolution().entityType());
        double  pepType = isPep ? 1.0 : 0.0;
        total += pepType * 0.20;
        if (isPep) {
            factors.add(new RiskFactor("pep-entity-type", 0.20,
                                       "entityType=PEP"));
        }

        double declinedValue = input.osintScreening().declined() ? 0.5 : 0.0;
        total += declinedValue * 0.10;
        if (input.osintScreening().declined()) {
            factors.add(new RiskFactor("osint-declined", 0.10,
                                       "screening declined — uncertainty factor (0.5)"));
        }

        double pepHit = input.osintScreening().pepHit() ? 1.0 : 0.0;
        total += pepHit * 0.10;
        if (input.osintScreening().pepHit()) {
            factors.add(new RiskFactor("osint-pep-hit", 0.10,
                                       "PEP database match"));
        }

        if (input.seniorAnalystReview() != null) {
            double adj = Math.max(-1.0, Math.min(1.0, input.seniorAnalystReview().riskAdjustment()));
            total += adj * 0.15;
            factors.add(new RiskFactor("senior-analyst-risk-adjustment", 0.15,
                                       "riskAdjustment=" + adj));
        }

        if (input.rejectionContext() != null) {
            total += 0.3 * 0.10;
            factors.add(new RiskFactor("rejection-uncertainty", 0.10,
                                       "gate rejected — uncertainty signal (0.3)"));
        }

        return new ScoringResult(Math.max(0.0, Math.min(1.0, total)), List.copyOf(factors));
    }
}
