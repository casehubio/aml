package io.casehub.aml.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SarOutcomeTest {

    @Test
    void upheld_verdict_accepted() {
        final SarOutcome o = new SarOutcome(SarVerdict.UPHELD, "SAR upheld by FinCEN", 0.92);
        assertEquals(SarVerdict.UPHELD, o.verdict());
        assertEquals("SAR upheld by FinCEN", o.reason());
        assertEquals(0.92, o.investigationAccuracyScore(), 0.001);
    }

    @Test
    void flagged_verdict_accepted() {
        final SarOutcome o = new SarOutcome(SarVerdict.FLAGGED, "Incomplete evidence chain", 0.30);
        assertEquals(SarVerdict.FLAGGED, o.verdict());
        assertEquals(0.30, o.investigationAccuracyScore(), 0.001);
    }

    @Test
    void score_at_zero_boundary_is_valid() {
        final SarOutcome o = new SarOutcome(SarVerdict.WITHDRAWN, "withdrawn", 0.0);
        assertEquals(0.0, o.investigationAccuracyScore(), 0.0);
    }

    @Test
    void score_at_one_boundary_is_valid() {
        final SarOutcome o = new SarOutcome(SarVerdict.UPHELD, "perfect score", 1.0);
        assertEquals(1.0, o.investigationAccuracyScore(), 0.0);
    }

    @Test
    void null_verdict_is_rejected() {
        assertThrows(NullPointerException.class,
                () -> new SarOutcome(null, "reason", 0.5));
    }

    @Test
    void null_reason_is_rejected() {
        assertThrows(NullPointerException.class,
                () -> new SarOutcome(SarVerdict.UPHELD, null, 0.5));
    }

    @Test
    void score_below_zero_is_rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new SarOutcome(SarVerdict.UPHELD, "bad", -0.001));
    }

    @Test
    void score_above_one_is_rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new SarOutcome(SarVerdict.UPHELD, "bad", 1.001));
    }

    @Test
    void non_finite_scores_are_rejected() {
        for (double score : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new SarOutcome(SarVerdict.UPHELD, "bad", score));
        }
    }

    @Test
    void all_verdicts_are_defined() {
        assertEquals(3, SarVerdict.values().length);
        assertNotNull(SarVerdict.valueOf("UPHELD"));
        assertNotNull(SarVerdict.valueOf("WITHDRAWN"));
        assertNotNull(SarVerdict.valueOf("FLAGGED"));
    }
}
