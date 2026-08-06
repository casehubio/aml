package io.casehub.aml.investigation;

import io.casehub.aml.domain.*;
import io.casehub.platform.api.preferences.PreferenceProvider;
import io.casehub.platform.api.preferences.Preferences;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TemplateSarNarrativeServiceTest {

    @Mock PreferenceProvider preferenceProvider;
    @Mock Preferences preferences;

    private TemplateSarNarrativeService service;

    private static final SuspiciousTransaction TX = new SuspiciousTransaction(
            "TX-001", "ACC-001", "ACC-002", BigDecimal.valueOf(50000), "USD",
            Instant.parse("2026-01-15T10:00:00Z"), FlagReason.HIGH_RISK_JURISDICTION);

    @BeforeEach
    void setUp() {
        when(preferenceProvider.resolve(any())).thenReturn(preferences);
        service = new TemplateSarNarrativeService(preferenceProvider);
    }

    @Test
    void unseeded_producesNarrativeFromFindings() {
        var ctx = new NarrativeContext(TX, null, null, null, List.of());
        var result = service.draft(ctx);
        assertFalse(result.seeded());
        assertEquals(0, result.seedCount());
        assertEquals(AdaptationMethod.DETERMINISTIC, result.adaptationMethod());
        assertNotNull(result.narrative());
        assertTrue(result.narrative().contains("TX-001"));
    }

    @Test
    void seeded_mirrorsStructureWithFacts() {
        var seed = new SeedNarrative("Template SAR for TX-OLD. Amount: 10000 USD.", 0.85, "STRUCTURING", "CORPORATE");
        var ctx = new NarrativeContext(TX, null, null, null, List.of(seed));
        var result = service.draft(ctx);
        assertTrue(result.seeded());
        assertEquals(1, result.seedCount());
        assertEquals(AdaptationMethod.DETERMINISTIC, result.adaptationMethod());
        assertTrue(result.narrative().contains("TX-001"));
        assertTrue(result.narrative().contains("50000"));
    }

    @Test
    void maxSeeds_truncatesExcessSeeds() {
        var seeds = List.of(
                new SeedNarrative("n1", 0.9, "A", "B"),
                new SeedNarrative("n2", 0.8, "A", "B"),
                new SeedNarrative("n3", 0.7, "A", "B"),
                new SeedNarrative("n4", 0.6, "A", "B"),
                new SeedNarrative("n5", 0.5, "A", "B"));
        var ctx = new NarrativeContext(TX, null, null, null, seeds);
        var result = service.draft(ctx);
        assertEquals(3, result.seedCount());
    }

    @Test
    void seeded_includesProvenanceNote() {
        var seed = new SeedNarrative("Template narrative.", 0.88, "STRUCTURING", "CORPORATE");
        var ctx = new NarrativeContext(TX, null, null, null, List.of(seed));
        var result = service.draft(ctx);
        assertTrue(result.narrative().contains("Adapted from"));
        assertTrue(result.narrative().contains("0.88"));
    }

    @Test
    void nullEntityPatternOsint_handledGracefully() {
        var ctx = new NarrativeContext(TX, null, null, null, List.of());
        assertDoesNotThrow(() -> service.draft(ctx));
    }
}
