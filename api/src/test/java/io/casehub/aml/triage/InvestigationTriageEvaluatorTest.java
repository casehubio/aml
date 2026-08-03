package io.casehub.aml.triage;

import io.casehub.aml.domain.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InvestigationTriageEvaluatorTest {

    private final InvestigationTriageEvaluator evaluator =
            new InvestigationTriageEvaluator(0.6, 0.25, 0.15, 0.3);

    private TriageInput input(String entityType, double riskScore,
                              boolean structuring, boolean pepHit,
                              boolean sanctionsHit, boolean declined,
                              CbrPathAdvice cbr) {
        return new TriageInput(
                new EntityResolutionResult("E-1", "chain", entityType, riskScore),
                new PatternAnalysisResult(structuring, "desc"),
                new OsintResult(sanctionsHit, pepHit, declined, "reason"),
                cbr);
    }

    @Test
    void hardGate_sanctionsHit_immediateReturn() {
        var result = evaluator.evaluate(
                input("INDIVIDUAL", 0.1, false, false, true, false, null));
        assertEquals(TriageDecision.SAR_WARRANTED, result.decision());
        assertEquals(HardGate.SANCTIONS_HIT, result.hardGate());
    }

    @Test
    void highScore_sarWarranted() {
        // PEP 0.9 + structuring: 0.9*0.35 + 0.25 + 0.20 = 0.765 > 0.6
        var result = evaluator.evaluate(
                input("PEP", 0.9, true, false, false, false, null));
        assertEquals(TriageDecision.SAR_WARRANTED, result.decision());
        assertNull(result.hardGate());
        assertFalse(result.factors().isEmpty());
    }

    @Test
    void lowScore_falsePositive() {
        // INDIVIDUAL 0.1: 0.1*0.35 = 0.035 < 0.25
        var result = evaluator.evaluate(
                input("INDIVIDUAL", 0.1, false, false, false, false, null));
        assertEquals(TriageDecision.FALSE_POSITIVE, result.decision());
    }

    @Test
    void ambiguousScore_inconclusive() {
        // CORPORATE 0.5 + structuring: 0.5*0.35 + 0.25 = 0.425 — between 0.25 and 0.6
        var result = evaluator.evaluate(
                input("CORPORATE", 0.5, true, false, false, false, null));
        assertEquals(TriageDecision.INCONCLUSIVE, result.decision());
    }

    @Test
    void cbrShiftsBorderline_toFalsePositive() {
        // CORPORATE 0.7: score = 0.7*0.35 = 0.245 — just below FP threshold 0.25
        // CBR FALSE_POSITIVE raises FP threshold → now above score → FALSE_POSITIVE
        var cbr = new CbrPathAdvice(10, 0.8, 0.7, "FALSE_POSITIVE", 0.9, false, true);
        var result = evaluator.evaluate(
                input("CORPORATE", 0.7, false, false, false, false, cbr));
        assertEquals(TriageDecision.FALSE_POSITIVE, result.decision());
        assertNotNull(result.cbrThresholdAdjustment());
    }

    @Test
    void cbrShiftsBorderline_toSarWarranted() {
        // CORPORATE 0.9 + structuring: 0.9*0.35 + 0.25 = 0.565 — just below SAR 0.6
        // CBR SAR_WARRANTED lowers SAR threshold → now below score → SAR_WARRANTED
        var cbr = new CbrPathAdvice(10, 0.8, 0.7, "SAR_WARRANTED", 0.9, false, true);
        var result = evaluator.evaluate(
                input("CORPORATE", 0.9, true, false, false, false, cbr));
        assertEquals(TriageDecision.SAR_WARRANTED, result.decision());
    }

    @Test
    void cbrCannotOverrideHardGate() {
        var cbr = new CbrPathAdvice(10, 0.9, 0.9, "FALSE_POSITIVE", 1.0, false, true);
        var result = evaluator.evaluate(
                input("PEP", 0.1, false, false, true, false, cbr));
        assertEquals(TriageDecision.SAR_WARRANTED, result.decision());
        assertEquals(HardGate.SANCTIONS_HIT, result.hardGate());
    }
}
