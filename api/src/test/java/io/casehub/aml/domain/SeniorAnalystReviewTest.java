package io.casehub.aml.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SeniorAnalystReviewTest {

    @Test
    void valid_construction() {
        var review = new SeniorAnalystReview(-0.15, "Entity structure legitimate", "LOWER_RISK");
        assertEquals(-0.15, review.riskAdjustment(), 0.001);
        assertEquals("Entity structure legitimate", review.finding());
        assertEquals("LOWER_RISK", review.recommendedAction());
    }

    @Test
    void risk_adjustment_at_lower_bound() {
        assertDoesNotThrow(() -> new SeniorAnalystReview(-1.0, "finding", "action"));
    }

    @Test
    void risk_adjustment_at_upper_bound() {
        assertDoesNotThrow(() -> new SeniorAnalystReview(1.0, "finding", "action"));
    }

    @Test
    void risk_adjustment_below_bound_throws() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> new SeniorAnalystReview(-1.1, "finding", "action"));
        assertTrue(ex.getMessage().contains("-1.1"));
    }

    @Test
    void risk_adjustment_above_bound_throws() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> new SeniorAnalystReview(1.1, "finding", "action"));
        assertTrue(ex.getMessage().contains("1.1"));
    }

    @Test
    void null_finding_throws() {
        assertThrows(NullPointerException.class,
                () -> new SeniorAnalystReview(0.0, null, "action"));
    }

    @Test
    void null_recommended_action_throws() {
        assertThrows(NullPointerException.class,
                () -> new SeniorAnalystReview(0.0, "finding", null));
    }

    @Test
    void zero_adjustment_valid() {
        var review = new SeniorAnalystReview(0.0, "neutral", "MAINTAIN");
        assertEquals(0.0, review.riskAdjustment(), 0.0001);
    }
}
