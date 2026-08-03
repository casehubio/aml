package io.casehub.aml.triage;

import io.casehub.aml.domain.CbrPathAdvice;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CbrAdjusterTest {

    private final CbrAdjuster adjuster = new CbrAdjuster(0.15, 0.3);

    @Test
    void nullAdvice_noAdjustment() {
        var result = adjuster.adjust(0.6, 0.25, null);
        assertEquals(0.6, result.sarThreshold());
        assertEquals(0.25, result.fpThreshold());
        assertNull(result.adjustment());
    }

    @Test
    void zeroCaseCount_noAdjustment() {
        var cbr = new CbrPathAdvice(0, 0.0, 0.0, null, null, false, true);
        var result = adjuster.adjust(0.6, 0.25, cbr);
        assertNull(result.adjustment());
    }

    @Test
    void lowConfidence_noAdjustment() {
        var cbr = new CbrPathAdvice(3, 0.5, 0.2, "SAR_WARRANTED", 0.8, false, true);
        var result = adjuster.adjust(0.6, 0.25, cbr);
        assertNull(result.adjustment());
    }

    @Test
    void error_noAdjustment() {
        var cbr = new CbrPathAdvice(5, 0.7, 0.6, "SAR_WARRANTED", 0.8, true, true);
        var result = adjuster.adjust(0.6, 0.25, cbr);
        assertNull(result.adjustment());
    }

    @Test
    void nullPredominantOutcome_noAdjustment() {
        var cbr = new CbrPathAdvice(5, 0.7, 0.6, null, null, false, true);
        var result = adjuster.adjust(0.6, 0.25, cbr);
        assertNull(result.adjustment());
    }

    @Test
    void predominantFalsePositive_raisesThresholds() {
        var cbr = new CbrPathAdvice(10, 0.8, 0.7, "FALSE_POSITIVE", 0.9, false, true);
        var result = adjuster.adjust(0.6, 0.25, cbr);
        assertTrue(result.sarThreshold() > 0.6);
        assertTrue(result.fpThreshold() > 0.25);
        assertNotNull(result.adjustment());
    }

    @Test
    void predominantSarWarranted_lowersThresholds() {
        var cbr = new CbrPathAdvice(10, 0.8, 0.7, "SAR_WARRANTED", 0.9, false, true);
        var result = adjuster.adjust(0.6, 0.25, cbr);
        assertTrue(result.sarThreshold() < 0.6);
        assertTrue(result.fpThreshold() < 0.25);
        assertNotNull(result.adjustment());
    }

    @Test
    void predominantInconclusive_noAdjustment() {
        var cbr = new CbrPathAdvice(10, 0.8, 0.7, "INCONCLUSIVE", 0.9, false, true);
        var result = adjuster.adjust(0.6, 0.25, cbr);
        assertNull(result.adjustment());
    }

    @Test
    void adjustmentCappedAtMax() {
        var cbr = new CbrPathAdvice(20, 0.95, 0.95, "FALSE_POSITIVE", 1.0, false, true);
        var result = adjuster.adjust(0.6, 0.25, cbr);
        assertTrue(result.sarThreshold() <= 0.6 + 0.15);
        assertTrue(result.fpThreshold() <= 0.25 + 0.15);
    }

    @Test
    void thresholdOrderingInvariant_clampedWhenViolated() {
        var cbr = new CbrPathAdvice(20, 0.95, 0.95, "FALSE_POSITIVE", 1.0, false, true);
        var result = adjuster.adjust(0.35, 0.30, cbr);
        assertTrue(result.sarThreshold() > result.fpThreshold());
    }

    @Test
    void unknownOutcome_noAdjustment() {
        var cbr = new CbrPathAdvice(5, 0.7, 0.6, "UNKNOWN_OUTCOME", 0.8, false, true);
        var result = adjuster.adjust(0.6, 0.25, cbr);
        assertNull(result.adjustment());
    }

    @Test
    void activeIsFalse_noAdjustmentRegardlessOfConfidence() {
        var cbr    = new CbrPathAdvice(10, 0.8, 0.7, "SAR_WARRANTED", 0.9, false, false);
        var result = adjuster.adjust(0.6, 0.25, cbr);
        assertNull(result.adjustment());
        assertEquals(0.6, result.sarThreshold());
        assertEquals(0.25, result.fpThreshold());
    }

}
