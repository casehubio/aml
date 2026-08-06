package io.casehub.aml.investigation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NarrativeResultTest {

    @Test
    void seededResult() {
        var result = new NarrativeResult("narrative", true, 3, AdaptationMethod.DETERMINISTIC);
        assertTrue(result.seeded());
        assertEquals(3, result.seedCount());
        assertEquals(AdaptationMethod.DETERMINISTIC, result.adaptationMethod());
    }

    @Test
    void unseededResult() {
        var result = new NarrativeResult("narrative", false, 0, AdaptationMethod.DETERMINISTIC);
        assertFalse(result.seeded());
        assertEquals(0, result.seedCount());
    }

    @Test
    void llmFallbackMethod() {
        var result = new NarrativeResult("narrative", true, 2, AdaptationMethod.LLM_FALLBACK_DETERMINISTIC);
        assertEquals(AdaptationMethod.LLM_FALLBACK_DETERMINISTIC, result.adaptationMethod());
    }
}
