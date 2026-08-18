package io.casehub.aml.triage;

import io.casehub.aml.domain.EntityResolutionResult;
import io.casehub.aml.domain.OsintResult;
import io.casehub.aml.domain.PatternAnalysisResult;
import io.casehub.aml.domain.RejectionContext;
import io.casehub.aml.domain.RiskFactor;
import io.casehub.aml.domain.SeniorAnalystReview;
import io.casehub.aml.domain.TriageInput;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiskScorerTest {

    private final RiskScorer scorer = new RiskScorer();

    private TriageInput input(String entityType, double riskScore,
                              boolean structuring, boolean pepHit,
                              boolean sanctionsHit, boolean declined) {
        return new TriageInput(
                new EntityResolutionResult("E-1", "chain", entityType, riskScore),
                new PatternAnalysisResult(structuring, "desc"),
                new OsintResult(sanctionsHit, pepHit, declined, "reason"),
                null);
    }

    @Test
    void highRisk_structuring_pep_yieldsHighScore() {
        var result = scorer.score(input("PEP", 0.9, true, false, false, false));
        // 0.9*0.35 + 1.0*0.25 + 1.0*0.20 + 0.0*0.10 + 0.0*0.10 = 0.865
        assertEquals(0.765, result.score(), 0.001);
    }

    @Test
    void lowRisk_noFlags_yieldsLowScore() {
        var result = scorer.score(input("INDIVIDUAL", 0.1, false, false, false, false));
        // 0.1*0.35 = 0.035
        assertEquals(0.035, result.score(), 0.001);
    }

    @Test
    void osintDeclined_contributesPartialScore() {
        var result = scorer.score(input("INDIVIDUAL", 0.1, false, false, false, true));
        // 0.1*0.35 + 0.5*0.10 = 0.035 + 0.05 = 0.085
        assertEquals(0.085, result.score(), 0.001);
    }

    @Test
    void scoreAlwaysInUnitRange() {
        var maxInput = input("PEP", 1.0, true, false, false, false);
        assertTrue(scorer.score(maxInput).score() <= 1.0);

        var minInput = input("INDIVIDUAL", 0.0, false, false, false, false);
        assertTrue(scorer.score(minInput).score() >= 0.0);
    }

    @Test
    void factorsListContainsContributingSignals() {
        var result = scorer.score(input("PEP", 0.5, true, false, false, false));
        assertTrue(result.factors().size() >= 3);
        var names = result.factors().stream().map(RiskFactor::name).toList();
        assertTrue(names.contains("entity-risk-score"));
        assertTrue(names.contains("structuring-detected"));
        assertTrue(names.contains("pep-entity-type"));
    }

    @Test
    void pepHit_osint_contributesWeight() {
        var withPepHit = scorer.score(input("CORPORATE", 0.3, false, true, false, false));
        var withoutPepHit = scorer.score(input("CORPORATE", 0.3, false, false, false, false));
        assertEquals(0.10, withPepHit.score() - withoutPepHit.score(), 0.001);
    }

    private TriageInput extendedInput(String entityType, double riskScore,
                                      boolean structuring, boolean pepHit,
                                      boolean sanctionsHit, boolean declined,
                                      SeniorAnalystReview review, RejectionContext rejection) {
        return new TriageInput(
                new EntityResolutionResult("E-1", "chain", entityType, riskScore),
                new PatternAnalysisResult(structuring, "desc"),
                new OsintResult(sanctionsHit, pepHit, declined, "reason"),
                null, review, rejection);
    }

    @Test
    void senior_analyst_negative_adjustment_reduces_score() {
        var review   = new SeniorAnalystReview(-0.5, "legitimate activity", "LOWER_RISK");
        var base     = scorer.score(input("CORPORATE", 0.8, false, false, false, false));
        var adjusted = scorer.score(extendedInput("CORPORATE", 0.8, false, false, false, false, review, null));
        assertTrue(adjusted.score() < base.score(),
                   "negative adjustment should reduce score: base=" + base.score() + " adjusted=" + adjusted.score());
    }

    @Test
    void senior_analyst_positive_adjustment_increases_score() {
        var review   = new SeniorAnalystReview(0.5, "risk confirmed", "MAINTAIN_RISK");
        var base     = scorer.score(input("CORPORATE", 0.3, false, false, false, false));
        var adjusted = scorer.score(extendedInput("CORPORATE", 0.3, false, false, false, false, review, null));
        assertTrue(adjusted.score() > base.score(),
                   "positive adjustment should increase score: base=" + base.score() + " adjusted=" + adjusted.score());
    }

    @Test
    void rejection_uncertainty_factor_present_when_rejection_context() {
        var rejection = new RejectionContext("sar.filing", "w1", "mlro", "reason");
        var result    = scorer.score(extendedInput("CORPORATE", 0.3, false, false, false, false, null, rejection));
        assertTrue(result.factors().stream().anyMatch(f -> f.name().equals("rejection-uncertainty")),
                   "should include rejection-uncertainty factor");
    }

    @Test
    void null_rejection_fields_produces_identical_score_to_base() {
        var base     = scorer.score(input("CORPORATE", 0.5, false, false, false, false));
        var extended = scorer.score(extendedInput("CORPORATE", 0.5, false, false, false, false, null, null));
        assertEquals(base.score(), extended.score(), 0.0001,
                     "null rejection fields should produce identical score");
    }

    @Test
    void score_clamped_to_unit_range_with_all_factors() {
        var review    = new SeniorAnalystReview(1.0, "max risk", "ESCALATE");
        var rejection = new RejectionContext("sar.filing", "w1", "mlro", "reason");
        var result    = scorer.score(extendedInput("PEP", 1.0, true, true, false, false, review, rejection));
        assertTrue(result.score() <= 1.0, "score must be <= 1.0, got " + result.score());
        assertTrue(result.score() >= 0.0, "score must be >= 0.0, got " + result.score());
    }

}
