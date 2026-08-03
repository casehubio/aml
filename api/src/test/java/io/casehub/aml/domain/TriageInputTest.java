package io.casehub.aml.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TriageInputTest {

    @Test
    void constructionWithNullCbrAdvice() {
        var input = new TriageInput(
                new EntityResolutionResult("E-1", "chain", "CORPORATE", 0.35),
                new PatternAnalysisResult(false, "no pattern"),
                new OsintResult(false, false, false, "clean"),
                null);
        assertNull(input.cbrPathAdvice());
        assertEquals("CORPORATE", input.entityResolution().entityType());
    }

    @Test
    void constructionWithCbrAdvice() {
        var cbr = new CbrPathAdvice(5, 0.7, 0.6, "SAR_WARRANTED", 0.8, false, true);
        var input = new TriageInput(
                new EntityResolutionResult("E-1", "chain", "PEP", 0.87),
                new PatternAnalysisResult(true, "structuring"),
                new OsintResult(false, false, false, "clean"),
                cbr);
        assertEquals("SAR_WARRANTED", input.cbrPathAdvice().predominantOutcome());
    }

    @Test
    void cbrPathAdvice_errorCase() {
        var cbr = new CbrPathAdvice(0, 0.0, 0.0, null, null, true, false);
        assertTrue(cbr.error());
        assertNull(cbr.predominantOutcome());
    }
}
